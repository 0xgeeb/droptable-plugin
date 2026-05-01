package com.droptable;

import com.droptable.DropTableModels.DropRow;
import com.droptable.DropTableModels.DropSection;
import com.droptable.DropTableModels.SearchResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class WikiDropService
{
	private static final String WIKI_BASE = "https://oldschool.runescape.wiki";
	private static final String API_BASE = WIKI_BASE + "/api.php";
	private static final Pattern HEADING_PATTERN = Pattern.compile("(?is)<h([2-4])[^>]*>.*?<span[^>]*class=\"mw-headline\"[^>]*>(.*?)</span>.*?</h\\1>");
	private static final Pattern TABLE_PATTERN = Pattern.compile("(?is)<table[^>]*class=\"[^\"]*wikitable[^\"]*\"[^>]*>.*?</table>");
	private static final Pattern ROW_PATTERN = Pattern.compile("(?is)<tr[^>]*>(.*?)</tr>");
	private static final Pattern CELL_PATTERN = Pattern.compile("(?is)<t([hd])[^>]*>(.*?)</t\\1>");
	private static final Pattern TAG_PATTERN = Pattern.compile("(?is)<[^>]+>");
	private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
	private static final List<String> RARITY_HEADERS = List.of("rarity", "drop rate");
	private static final List<String> QUANTITY_HEADERS = List.of("quantity", "qty", "amount");
	private static final List<String> ITEM_HEADERS = List.of("item", "items");
	private static final String USER_AGENT = "RuneLite Drop Table Plugin/1.0 (+https://github.com/runelite/plugin-hub)";

	private final HttpClient httpClient = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.build();

	@Inject
	WikiDropService()
	{
	}

	SearchResult search(String query) throws IOException, InterruptedException
	{
		String normalized = query == null ? "" : query.trim();
		if (normalized.isEmpty())
		{
			throw new IOException("Enter an enemy or boss name.");
		}

		String title = resolveTitle(normalized);
		String html = fetchParsedHtml(title);
		List<DropSection> sections = parseDropSections(html);
		if (sections.isEmpty())
		{
			throw new IOException("No drop tables were found on the wiki page.");
		}

		return new SearchResult(title, WIKI_BASE + "/w/" + encodePageTitle(title), sections);
	}

	private String resolveTitle(String query) throws IOException, InterruptedException
	{
		String url = API_BASE
			+ "?action=opensearch"
			+ "&limit=1"
			+ "&namespace=0"
			+ "&format=json"
			+ "&search=" + urlEncode(query);
		JsonArray root = JsonParser.parseString(send(url)).getAsJsonArray();
		JsonArray titles = root.get(1).getAsJsonArray();
		if (titles.isEmpty())
		{
			throw new IOException("No wiki page matched \"" + query + "\".");
		}
		return titles.get(0).getAsString();
	}

	private String fetchParsedHtml(String title) throws IOException, InterruptedException
	{
		String url = API_BASE
			+ "?action=parse"
			+ "&prop=text"
			+ "&format=json"
			+ "&formatversion=2"
			+ "&page=" + urlEncode(title);
		JsonObject root = JsonParser.parseString(send(url)).getAsJsonObject();
		JsonObject parse = root.getAsJsonObject("parse");
		if (parse == null || !parse.has("text"))
		{
			throw new IOException("The wiki page could not be parsed.");
		}
		return parse.get("text").getAsString();
	}

	private List<DropSection> parseDropSections(String html)
	{
		List<Heading> headings = extractHeadings(html);
		List<TableBlock> tables = extractTables(html);
		List<DropSection> sections = new ArrayList<>();

		for (TableBlock table : tables)
		{
			List<String> headers = extractHeaders(table.html);
			int itemIndex = findHeader(headers, ITEM_HEADERS);
			int quantityIndex = findHeader(headers, QUANTITY_HEADERS);
			int rarityIndex = findHeader(headers, RARITY_HEADERS);
			if (itemIndex < 0 || rarityIndex < 0)
			{
				continue;
			}

			List<DropRow> rows = extractRows(table.html, itemIndex, quantityIndex, rarityIndex);
			if (rows.isEmpty())
			{
				continue;
			}

			String sectionName = findNearestHeading(headings, table.startIndex);
			sections.add(new DropSection(sectionName, rows));
		}

		return sections;
	}

	private List<Heading> extractHeadings(String html)
	{
		List<Heading> headings = new ArrayList<>();
		Matcher matcher = HEADING_PATTERN.matcher(html);
		while (matcher.find())
		{
			String name = cleanText(matcher.group(2));
			if (!name.isEmpty())
			{
				headings.add(new Heading(matcher.start(), name));
			}
		}
		headings.sort(Comparator.comparingInt(heading -> heading.position));
		return headings;
	}

	private List<TableBlock> extractTables(String html)
	{
		List<TableBlock> tables = new ArrayList<>();
		Matcher matcher = TABLE_PATTERN.matcher(html);
		while (matcher.find())
		{
			tables.add(new TableBlock(matcher.start(), matcher.group()));
		}
		return tables;
	}

	private List<String> extractHeaders(String tableHtml)
	{
		Matcher rowMatcher = ROW_PATTERN.matcher(tableHtml);
		while (rowMatcher.find())
		{
			List<String> cells = extractCells(rowMatcher.group(1));
			if (!cells.isEmpty())
			{
				return cells;
			}
		}
		return List.of();
	}

	private List<DropRow> extractRows(String tableHtml, int itemIndex, int quantityIndex, int rarityIndex)
	{
		List<DropRow> rows = new ArrayList<>();
		Matcher rowMatcher = ROW_PATTERN.matcher(tableHtml);
		boolean headerSkipped = false;
		while (rowMatcher.find())
		{
			List<String> cells = extractCells(rowMatcher.group(1));
			if (cells.isEmpty())
			{
				continue;
			}
			if (!headerSkipped)
			{
				headerSkipped = true;
				continue;
			}
			if (itemIndex >= cells.size() || rarityIndex >= cells.size())
			{
				continue;
			}

			String item = cells.get(itemIndex);
			String quantity = quantityIndex >= 0 && quantityIndex < cells.size() ? cells.get(quantityIndex) : "";
			String rarity = cells.get(rarityIndex);
			String notes = buildNotes(cells, itemIndex, quantityIndex, rarityIndex);
			if (item.isEmpty() || rarity.isEmpty())
			{
				continue;
			}
			rows.add(new DropRow(item, quantity, rarity, notes));
		}
		return rows;
	}

	private List<String> extractCells(String rowHtml)
	{
		List<String> cells = new ArrayList<>();
		Matcher cellMatcher = CELL_PATTERN.matcher(rowHtml);
		while (cellMatcher.find())
		{
			String cleaned = cleanText(cellMatcher.group(2));
			if (!cleaned.isEmpty())
			{
				cells.add(cleaned);
			}
		}
		return cells;
	}

	private int findHeader(List<String> headers, List<String> candidates)
	{
		for (int i = 0; i < headers.size(); i++)
		{
			String normalized = headers.get(i).toLowerCase(Locale.ROOT);
			for (String candidate : candidates)
			{
				if (normalized.equals(candidate) || normalized.contains(candidate))
				{
					return i;
				}
			}
		}
		return -1;
	}

	private String findNearestHeading(List<Heading> headings, int position)
	{
		String fallback = "Drops";
		for (Heading heading : headings)
		{
			if (heading.position > position)
			{
				break;
			}
			fallback = heading.name;
		}
		return fallback;
	}

	private String buildNotes(List<String> cells, int itemIndex, int quantityIndex, int rarityIndex)
	{
		List<String> notes = new ArrayList<>();
		for (int i = 0; i < cells.size(); i++)
		{
			if (i == itemIndex || i == quantityIndex || i == rarityIndex)
			{
				continue;
			}
			notes.add(cells.get(i));
		}
		return String.join(" | ", notes);
	}

	private String send(String url) throws IOException, InterruptedException
	{
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.timeout(Duration.ofSeconds(20))
			.header("User-Agent", USER_AGENT)
			.header("Accept", "application/json")
			.GET()
			.build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 300)
		{
			throw new IOException("Wiki request failed with status " + response.statusCode() + ".");
		}
		return response.body();
	}

	private String cleanText(String input)
	{
		String text = input
			.replace("<br>", "\n")
			.replace("<br/>", "\n")
			.replace("<br />", "\n");
		text = TAG_PATTERN.matcher(text).replaceAll(" ");
		text = decodeEntities(text);
		text = WHITESPACE_PATTERN.matcher(text).replaceAll(" ").trim();
		return text;
	}

	private String decodeEntities(String text)
	{
		return text
			.replace("&nbsp;", " ")
			.replace("&amp;", "&")
			.replace("&quot;", "\"")
			.replace("&#39;", "'")
			.replace("&apos;", "'")
			.replace("&lt;", "<")
			.replace("&gt;", ">");
	}

	private String urlEncode(String value)
	{
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private String encodePageTitle(String title)
	{
		return title.replace(' ', '_');
	}

	private static final class Heading
	{
		private final int position;
		private final String name;

		private Heading(int position, String name)
		{
			this.position = position;
			this.name = name;
		}
	}

	private static final class TableBlock
	{
		private final int startIndex;
		private final String html;

		private TableBlock(int startIndex, String html)
		{
			this.startIndex = startIndex;
			this.html = html;
		}
	}
}
