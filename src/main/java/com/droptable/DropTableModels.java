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
		private final int priority;
		private final List<DropRow> rows;

		DropSection(String name, int priority, List<DropRow> rows)
		{
			this.name = name;
			this.priority = priority;
			this.rows = rows;
		}

		String getName()
		{
			return name;
		}

		int getPriority()
		{
			return priority;
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
		private final String price;
		private final String notes;
		private final double rarityScore;

		DropRow(String item, String quantity, String rarity, String price, String notes, double rarityScore)
		{
			this.item = item;
			this.quantity = quantity;
			this.rarity = rarity;
			this.price = price;
			this.notes = notes;
			this.rarityScore = rarityScore;
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

		String getPrice()
		{
			return price;
		}

		String getNotes()
		{
			return notes;
		}

		double getRarityScore()
		{
			return rarityScore;
		}
	}
}
