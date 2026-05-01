package com.droptable;

import com.droptable.DropTableModels.DropRow;
import com.droptable.DropTableModels.DropSection;
import com.droptable.DropTableModels.SearchResult;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

class DropTablePanel extends PluginPanel
{
	private final ExecutorService executor;
	private final Consumer<String> searchAction;
	private final JTextField searchField = new JTextField();
	private final JButton searchButton = new JButton("Search");
	private final JLabel statusLabel = new JLabel("Search the OSRS Wiki for a boss or enemy.");
	private final JEditorPane resultsPane = new JEditorPane("text/html", "");

	DropTablePanel(ExecutorService executor, Consumer<String> searchAction)
	{
		super(false);
		this.executor = executor;
		this.searchAction = searchAction;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setOpaque(false);

		searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		searchField.addActionListener(this::submitSearch);

		searchButton.addActionListener(this::submitSearch);
		searchButton.setAlignmentX(LEFT_ALIGNMENT);

		statusLabel.setAlignmentX(LEFT_ALIGNMENT);

		top.add(searchField);
		top.add(Box.createVerticalStrut(8));
		top.add(searchButton);
		top.add(Box.createVerticalStrut(8));
		top.add(statusLabel);

		resultsPane.setEditable(false);
		resultsPane.setOpaque(false);
		resultsPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
		resultsPane.setText(emptyStateHtml());

		JScrollPane scrollPane = new JScrollPane(resultsPane);
		scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

		add(top, BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);
	}

	void focusSearch()
	{
		SwingUtilities.invokeLater(searchField::requestFocusInWindow);
	}

	void renderLoading(String query)
	{
		statusLabel.setText("Searching for " + query + "...");
		searchButton.setEnabled(false);
		resultsPane.setText(loadingStateHtml(query));
	}

	void renderResult(SearchResult result)
	{
		statusLabel.setText("Showing drop tables for " + result.getTitle() + ".");
		searchButton.setEnabled(true);
		resultsPane.setText(toHtml(result));
		resultsPane.setCaretPosition(0);
	}

	void renderError(String message)
	{
		statusLabel.setText(message);
		searchButton.setEnabled(true);
		resultsPane.setText(errorStateHtml(message));
		resultsPane.setCaretPosition(0);
	}

	private void submitSearch(ActionEvent ignored)
	{
		String query = searchField.getText().trim();
		if (query.isEmpty())
		{
			renderError("Enter an enemy or boss name.");
			return;
		}
		renderLoading(query);
		executor.submit(() -> searchAction.accept(query));
	}

	private String toHtml(SearchResult result)
	{
		StringBuilder html = new StringBuilder();
		html.append("<html><body style='font-family:sans-serif;color:#dddddd;background:#2b2b2b;'>");
		html.append("<h2>").append(escape(result.getTitle())).append("</h2>");
		html.append("<p><a href='").append(result.getPageUrl()).append("'>")
			.append(escape(result.getPageUrl())).append("</a></p>");

		for (DropSection section : result.getSections())
		{
			html.append("<h3>").append(escape(section.getName())).append("</h3>");
			html.append("<table width='100%' cellspacing='0' cellpadding='4' style='border-collapse:collapse;'>");
			html.append("<tr bgcolor='#3d3d3d'><th align='left'>Item</th><th align='left'>Qty</th><th align='left'>Rarity</th></tr>");
			for (DropRow row : section.getRows())
			{
				html.append("<tr>")
					.append("<td>").append(escape(row.getItem())).append(notesSuffix(row.getNotes())).append("</td>")
					.append("<td>").append(escape(defaultText(row.getQuantity(), "-"))).append("</td>")
					.append("<td>").append(escape(row.getRarity())).append("</td>")
					.append("</tr>");
			}
			html.append("</table>");
		}

		html.append("</body></html>");
		return html.toString();
	}

	private String notesSuffix(String notes)
	{
		if (notes == null || notes.isBlank())
		{
			return "";
		}
		return "<br/><span style='color:#a0a0a0;font-size:10px;'>" + escape(notes) + "</span>";
	}

	private String emptyStateHtml()
	{
		return "<html><body style='font-family:sans-serif;color:#bbbbbb;background:#2b2b2b;'>"
			+ "<p>Enter a monster or boss name to load its wiki drop table.</p>"
			+ "</body></html>";
	}

	private String loadingStateHtml(String query)
	{
		return "<html><body style='font-family:sans-serif;color:#bbbbbb;background:#2b2b2b;'>"
			+ "<p>Loading drop tables for <b>" + escape(defaultText(query, "your search")) + "</b>...</p>"
			+ "</body></html>";
	}

	private String errorStateHtml(String message)
	{
		return "<html><body style='font-family:sans-serif;color:#ff9d9d;background:#2b2b2b;'>"
			+ "<p>" + escape(message) + "</p>"
			+ "</body></html>";
	}

	private String escape(String text)
	{
		return defaultText(text, "")
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;");
	}

	private String defaultText(String text, String fallback)
	{
		return text == null || text.isBlank() ? fallback : text;
	}
}
