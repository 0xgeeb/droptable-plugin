package com.droptable;

import com.droptable.DropTableModels.SearchResult;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@Slf4j
@PluginDescriptor(
	name = "Drop Table",
	description = "Searches the OSRS Wiki and shows enemy and boss drop tables in the sidebar",
	tags = {"drops", "wiki", "boss", "monster", "loot"}
)
public class DropTablePlugin extends Plugin
{
	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private WikiDropService wikiDropService;

	private ExecutorService executorService;
	private DropTablePanel panel;
	private NavigationButton navigationButton;

	@Override
	protected void startUp()
	{
		executorService = Executors.newSingleThreadExecutor();
		panel = new DropTablePanel(executorService, this::runSearch, this::runSuggestions);
		navigationButton = NavigationButton.builder()
			.tooltip("Drop Table")
			.icon(createIcon())
			.priority(5)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);
		panel.focusSearch();
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navigationButton);
		navigationButton = null;
		panel = null;
		if (executorService != null)
		{
			executorService.shutdownNow();
			executorService = null;
		}
	}

	@Provides
	DropTableConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DropTableConfig.class);
	}

	private void runSearch(String query)
	{
		try
		{
			SearchResult result = wikiDropService.search(query);
			SwingUtilities.invokeLater(() -> {
				if (panel != null)
				{
					panel.renderResult(result);
				}
			});
		}
		catch (IOException | InterruptedException ex)
		{
			if (ex instanceof InterruptedException)
			{
				Thread.currentThread().interrupt();
			}
			log.debug("Failed to load drop table for {}", query, ex);
			String message = ex.getMessage() == null ? "Unable to load the wiki drop table." : ex.getMessage();
			SwingUtilities.invokeLater(() -> {
				if (panel != null)
				{
					panel.renderError(message);
				}
			});
		}
	}

	private void runSuggestions(String query)
	{
		try
		{
			java.util.List<String> suggestions = wikiDropService.suggest(query);
			SwingUtilities.invokeLater(() -> {
				if (panel != null)
				{
					panel.renderSuggestions(query, suggestions);
				}
			});
		}
		catch (IOException | InterruptedException ex)
		{
			if (ex instanceof InterruptedException)
			{
				Thread.currentThread().interrupt();
			}
			log.debug("Failed to load suggestions for {}", query, ex);
		}
	}

	private BufferedImage createIcon()
	{
		BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(new Color(42, 42, 42));
		graphics.fillRoundRect(0, 0, 32, 32, 8, 8);
		graphics.setColor(new Color(255, 196, 84));
		graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		graphics.drawString("DT", 5, 20);
		graphics.dispose();
		return image;
	}
}
