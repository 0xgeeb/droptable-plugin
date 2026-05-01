package com.droptable;

import java.util.List;

final class DropTableModels
{
	private DropTableModels()
	{
	}

	static final class SearchResult
	{
		private final String title;
		private final String pageUrl;
		private final List<DropSection> sections;

		SearchResult(String title, String pageUrl, List<DropSection> sections)
		{
			this.title = title;
			this.pageUrl = pageUrl;
			this.sections = sections;
		}

		String getTitle()
		{
			return title;
		}

		String getPageUrl()
		{
			return pageUrl;
		}

		List<DropSection> getSections()
		{
			return sections;
		}
	}

	static final class DropSection
	{
		private final String name;
		private final List<DropRow> rows;

		DropSection(String name, List<DropRow> rows)
		{
			this.name = name;
			this.rows = rows;
		}

		String getName()
		{
			return name;
		}

		List<DropRow> getRows()
		{
			return rows;
		}
	}

	static final class DropRow
	{
		private final String item;
		private final String quantity;
		private final String rarity;
		private final String notes;

		DropRow(String item, String quantity, String rarity, String notes)
		{
			this.item = item;
			this.quantity = quantity;
			this.rarity = rarity;
			this.notes = notes;
		}

		String getItem()
		{
			return item;
		}

		String getQuantity()
		{
			return quantity;
		}

		String getRarity()
		{
			return rarity;
		}

		String getNotes()
		{
			return notes;
		}
	}
}
