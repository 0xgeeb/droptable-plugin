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
import java.util.Optional;
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
	private static final Pattern CAPTION_PATTERN = Pattern.compile("(?is)<caption[^>]*>(.*?)</caption>");
	private static final Pattern TAG_PATTERN = Pattern.compile("(?is)<[^>]+>");
	private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
	private static final Pattern RARITY_PATTERN = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*/\\s*(\\d+(?:\\.\\d+)?)");
	private static final Pattern MULTIPLIER_PATTERN = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*[x×]\\s*(\\d+(?:\\.\\d+)?)\\s*/\\s*(\\d+(?:\\.\\d+)?)");
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
		String html = fetchBestDropHtml(title);
		List<DropSection> sections = parseDropSections(html);
		if (sections.isEmpty())
		{
			throw new IOException("No drop tables were found on the wiki page.");
		}

		return new SearchResult(title, WIKI_BASE + "/w/" + encodePageTitle(title), sections);
	}

	List<String> suggest(String query) throws IOException, InterruptedException
	{
		String normalized = query == null ? "" : query.trim();
		if (normalized.length() < 2)
		{
			return List.of();
		}

		String url = API_BASE
			+ "?action=opensearch"
			+ "&limit=8"
			+ "&namespace=0"
			+ "&format=json"
			+ "&search=" + urlEncode(normalized);
		JsonArray root = new JsonParser().parse(send(url)).getAsJsonArray();
		JsonArray titles = root.get(1).getAsJsonArray();
		List<String> suggestions = new ArrayList<>();
		for (int i = 0; i < titles.size(); i++)
		{
			String title = titles.get(i).getAsString();
			if (!title.toLowerCase(Locale.ROOT).contains("drop table"))
			{
				suggestions.add(title);
			}
		}
		return suggestions;
	}

	private String resolveTitle(String query) throws IOException, InterruptedException
	{
		String url = API_BASE
			+ "?action=opensearch"
			+ "&limit=1"
			+ "&namespace=0"
			+ "&format=json"
			+ "&search=" + urlEncode(query);
		JsonArray root = new JsonParser().parse(send(url)).getAsJsonArray();
		JsonArray titles = root.get(1).getAsJsonArray();
		if (titles.size() == 0)
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
		JsonObject root = new JsonParser().parse(send(url)).getAsJsonObject();
		JsonObject parse = root.getAsJsonObject("parse");
		if (parse == null || !parse.has("text"))
		{
			throw new IOException("The wiki page could not be parsed.");
		}
		return parse.get("text").getAsString();
	}

	private String fetchBestDropHtml(String title) throws IOException, InterruptedException
	{
		List<String> candidateTitles = List.of(title + " drop table", title);
		Optional<String> bestHtml = Optional.empty();
		int bestScore = -1;

		for (String candidateTitle : candidateTitles)
		{
			try
			{
				String html = fetchParsedHtml(candidateTitle);
				int score = scoreDropHtml(html);
				if (score > bestScore)
				{
					bestScore = score;
					bestHtml = Optional.of(html);
				}
			}
			catch (IOException ex)
			{
				// Fall through to the next candidate.
			}
		}

		if (bestHtml.isPresent())
		{
			return bestHtml.get();
		}

		throw new IOException("The wiki page could not be parsed.");
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

			String sectionName = determineSectionName(table, headings);
			rows.sort(Comparator.comparingDouble(DropRow::getRarityScore).reversed()
				.thenComparing(DropRow::getItem));
			sections.add(new DropSection(sectionName, rankSection(sectionName), rows));
		}

		sections.sort(Comparator.comparingInt(DropSection::getPriority).thenComparing(DropSection::getName));
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
			tables.add(new TableBlock(matcher.start(), matcher.group(), html));
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
			rows.add(new DropRow(item, quantity, rarity, notes, parseRarityScore(rarity)));
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

	private String determineSectionName(TableBlock table, List<Heading> headings)
	{
		String caption = extractCaption(table.html);
		if (!caption.isEmpty())
		{
			return normalizeSectionName(caption);
		}

		String fallback = "Drops";
		for (Heading heading : headings)
		{
			if (heading.position > table.startIndex)
			{
				break;
			}
			fallback = heading.name;
		}

		if ("Drops".equalsIgnoreCase(fallback))
		{
			String contextual = extractContextualLabel(table);
			if (!contextual.isEmpty())
			{
				return normalizeSectionName(contextual);
			}
		}

		return normalizeSectionName(fallback);
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

	private String extractCaption(String tableHtml)
	{
		Matcher matcher = CAPTION_PATTERN.matcher(tableHtml);
		if (matcher.find())
		{
			return cleanText(matcher.group(1));
		}
		return "";
	}

	private String extractContextualLabel(TableBlock table)
	{
		int contextStart = Math.max(0, table.startIndex - 240);
		String prefix = table.fullHtml.substring(contextStart, table.startIndex);
		List<Heading> localHeadings = extractHeadings(prefix);
		if (!localHeadings.isEmpty())
		{
			return localHeadings.get(localHeadings.size() - 1).name;
		}
		String cleaned = cleanText(prefix);
		String[] parts = cleaned.split("\\.");
		if (parts.length == 0)
		{
			return "";
		}
		return parts[parts.length - 1].trim();
	}

	private int scoreDropHtml(String html)
	{
		int score = 0;
		for (String keyword : List.of("tertiary", "unique", "100%", "mutagen", "rare drop table"))
		{
			if (html.toLowerCase(Locale.ROOT).contains(keyword))
			{
				score += 5;
			}
		}
		score += extractTables(html).size();
		return score;
	}

	private int rankSection(String sectionName)
	{
		String normalized = sectionName.toLowerCase(Locale.ROOT);
		if (normalized.contains("tertiary"))
		{
			return 0;
		}
		if (normalized.contains("unique") || normalized.contains("mutagen") || normalized.contains("pet"))
		{
			return 1;
		}
		if (normalized.contains("rare"))
		{
			return 2;
		}
		if (normalized.contains("100%") || normalized.contains("always"))
		{
			return 3;
		}
		if (normalized.contains("resources") || normalized.contains("weapons") || normalized.contains("armour"))
		{
			return 4;
		}
		if (normalized.contains("other"))
		{
			return 5;
		}
		return 6;
	}

	private String normalizeSectionName(String sectionName)
	{
		String cleaned = sectionName == null ? "" : sectionName.trim();
		if (cleaned.isEmpty())
		{
			return "Drops";
		}
		if (cleaned.equalsIgnoreCase("drops"))
		{
			return "General Drops";
		}
		return cleaned;
	}

	private double parseRarityScore(String rarity)
	{
		String normalized = rarity.toLowerCase(Locale.ROOT);
		if (normalized.contains("always"))
		{
			return 1d;
		}

		Matcher multiplied = MULTIPLIER_PATTERN.matcher(normalized);
		if (multiplied.find())
		{
			double multiplier = parseNumber(multiplied.group(1));
			double numerator = parseNumber(multiplied.group(2));
			double denominator = parseNumber(multiplied.group(3));
			if (multiplier > 0d && numerator > 0d)
			{
				return denominator / (multiplier * numerator);
			}
		}

		Matcher matcher = RARITY_PATTERN.matcher(normalized);
		if (matcher.find())
		{
			double numerator = parseNumber(matcher.group(1));
			double denominator = parseNumber(matcher.group(2));
			if (numerator > 0d)
			{
				return denominator / numerator;
			}
		}

		return 0d;
	}

	private double parseNumber(String value)
	{
		try
		{
			return Double.parseDouble(value);
		}
		catch (NumberFormatException ex)
		{
			return 0d;
		}
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
		private final String fullHtml;

		private TableBlock(int startIndex, String html, String fullHtml)
		{
			this.startIndex = startIndex;
			this.html = html;
			this.fullHtml = fullHtml;
		}
	}
}
