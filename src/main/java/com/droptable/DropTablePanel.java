package com.droptable;

import com.droptable.DropTableModels.DropRow;
import com.droptable.DropTableModels.DropSection;
import com.droptable.DropTableModels.SearchResult;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

class DropTablePanel extends PluginPanel
{
	private static final Color TEXT = new Color(223, 223, 223);
	private static final Color MUTED = new Color(157, 161, 167);
	private static final Color HEADER = new Color(255, 204, 102);
	private static final Color DIVIDER = new Color(52, 56, 62);
	private static final Color SUGGESTION_SELECTED = new Color(61, 68, 79);
	private static final Color TABLE_HEADER = new Color(60, 52, 42);
	private static final Color TABLE_ROW = new Color(38, 38, 38);
	private static final Color RATE_COMMON = new Color(34, 116, 62);
	private static final Color RATE_UNCOMMON = new Color(130, 116, 34);
	private static final Color RATE_RARE = new Color(132, 74, 37);
	private static final int ROW_WRAP_WIDTH = 205;
	private static final float TITLE_FONT_SIZE = 18f;
	private static final float BODY_FONT_SIZE = 15f;
	private static final float META_FONT_SIZE = 14f;

	private final ExecutorService executor;
	private final Consumer<String> searchAction;
	private final Consumer<String> suggestionAction;
	private final JTextField searchField = new JTextField();
	private final JButton searchButton = new JButton("Search");
	private final JLabel monsterLabel = new JLabel("Drop Table");
	private final JLabel statusLabel = new JLabel("Start typing a monster or boss name.");
	private final JPanel resultsPanel = new JPanel();
	private final DefaultListModel<String> suggestionModel = new DefaultListModel<>();
	private final JList<String> suggestionList = new JList<>(suggestionModel);
	private final JPopupMenu suggestionPopup = new JPopupMenu();
	private boolean applyingSuggestion;

	DropTablePanel(ExecutorService executor, Consumer<String> searchAction, Consumer<String> suggestionAction)
	{
		super(false);
		this.executor = executor;
		this.searchAction = searchAction;
		this.suggestionAction = suggestionAction;

		setLayout(new BorderLayout(0, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JPanel top = new JPanel();
		top.setOpaque(false);
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.add(createSearchControls());
		top.add(Box.createVerticalStrut(8));

		monsterLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		monsterLabel.setForeground(TEXT);
		monsterLabel.setFont(monsterLabel.getFont().deriveFont(Font.BOLD, TITLE_FONT_SIZE));
		top.add(monsterLabel);

		statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		statusLabel.setForeground(MUTED);
		statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, META_FONT_SIZE));
		top.add(statusLabel);

		resultsPanel.setOpaque(false);
		resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
		renderEmptyState();

		JScrollPane scrollPane = new JScrollPane(resultsPanel);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.getViewport().setOpaque(false);
		scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.setOpaque(false);

		add(top, BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);
	}

	void focusSearch()
	{
		SwingUtilities.invokeLater(searchField::requestFocusInWindow);
	}

	void renderLoading(String query)
	{
		monsterLabel.setText("Drop Table");
		statusLabel.setText("Searching for " + query + "...");
		searchButton.setEnabled(false);
		clearSuggestions();
		resultsPanel.removeAll();
		resultsPanel.add(createMessageLabel("Loading drop table..."));
		repaintResults();
	}

	void renderResult(SearchResult result)
	{
		monsterLabel.setText(result.getTitle());
		statusLabel.setText("Wiki drop table sections.");
		searchButton.setEnabled(true);
		clearSuggestions();
		resultsPanel.removeAll();

		for (DropSection section : result.getSections())
		{
			resultsPanel.add(createSectionPanel(section));
			resultsPanel.add(Box.createVerticalStrut(8));
		}

		repaintResults();
	}

	void renderError(String message)
	{
		monsterLabel.setText("Drop Table");
		statusLabel.setText(message);
		searchButton.setEnabled(true);
		resultsPanel.removeAll();
		resultsPanel.add(createMessageLabel(message));
		repaintResults();
	}

	void renderSuggestions(String typedQuery, List<String> suggestions)
	{
		if (!searchField.getText().trim().equalsIgnoreCase(typedQuery.trim()))
		{
			return;
		}

		suggestionModel.clear();
		for (String suggestion : suggestions)
		{
			suggestionModel.addElement(suggestion);
		}

		if (suggestionModel.isEmpty())
		{
			suggestionPopup.setVisible(false);
			return;
		}

		suggestionList.setSelectedIndex(0);
		suggestionPopup.show(searchField, 0, searchField.getHeight());
	}

	private JPanel createSearchControls()
	{
		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setOpaque(false);
		top.setAlignmentX(Component.LEFT_ALIGNMENT);
		top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

		searchField.addActionListener(this::submitSearch);
		searchField.setFont(searchField.getFont().deriveFont(Font.PLAIN, BODY_FONT_SIZE));
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				requestSuggestions();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				requestSuggestions();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				requestSuggestions();
			}
		});
		searchField.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (!suggestionPopup.isVisible())
				{
					return;
				}

				if (e.getKeyCode() == KeyEvent.VK_DOWN)
				{
					suggestionList.setSelectedIndex(Math.min(suggestionModel.getSize() - 1, suggestionList.getSelectedIndex() + 1));
					e.consume();
				}
				else if (e.getKeyCode() == KeyEvent.VK_UP)
				{
					suggestionList.setSelectedIndex(Math.max(0, suggestionList.getSelectedIndex() - 1));
					e.consume();
				}
				else if (e.getKeyCode() == KeyEvent.VK_ENTER)
				{
					applySelectedSuggestion();
					e.consume();
				}
				else if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
				{
					clearSuggestions();
					e.consume();
				}
			}
		});

		searchButton.addActionListener(this::submitSearch);
		searchButton.setFont(searchButton.getFont().deriveFont(Font.BOLD, META_FONT_SIZE));

		suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		suggestionList.setFocusable(false);
		suggestionList.setCellRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
			{
				JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				label.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
				label.setBackground(isSelected ? SUGGESTION_SELECTED : ColorScheme.DARK_GRAY_COLOR);
				label.setForeground(TEXT);
				label.setFont(label.getFont().deriveFont(Font.PLAIN, BODY_FONT_SIZE));
				return label;
			}
		});
		suggestionList.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				applySelectedSuggestion();
			}
		});

		JScrollPane suggestionScroll = new JScrollPane(suggestionList);
		suggestionScroll.setBorder(BorderFactory.createLineBorder(DIVIDER));
		suggestionScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		suggestionScroll.setPreferredSize(new Dimension(230, 150));
		suggestionPopup.setBorder(BorderFactory.createEmptyBorder());
		suggestionPopup.add(suggestionScroll);

		top.add(searchField, BorderLayout.CENTER);
		top.add(searchButton, BorderLayout.EAST);
		return top;
	}

	private JPanel createSectionPanel(DropSection section)
	{
		JPanel sectionPanel = new JPanel();
		sectionPanel.setOpaque(false);
		sectionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		sectionPanel.setLayout(new BoxLayout(sectionPanel, BoxLayout.Y_AXIS));
		sectionPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, DIVIDER));

		JLabel sectionLabel = new JLabel(section.getName());
		sectionLabel.setForeground(HEADER);
		sectionLabel.setFont(sectionLabel.getFont().deriveFont(Font.BOLD, BODY_FONT_SIZE));
		sectionLabel.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));
		sectionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		sectionPanel.add(sectionLabel);
		sectionPanel.add(createHeaderRow());

		for (DropRow row : section.getRows())
		{
			sectionPanel.add(createRowPanel(row));
		}

		return sectionPanel;
	}

	private JPanel createHeaderRow()
	{
		JPanel rowPanel = new JPanel(new BorderLayout(0, 0));
		rowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		rowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		rowPanel.setBackground(TABLE_HEADER);
		rowPanel.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, DIVIDER));

		rowPanel.add(createCell("Item  |  Qty  |  Rate  |  Price", TABLE_HEADER, Font.BOLD), BorderLayout.CENTER);
		return rowPanel;
	}

	private JPanel createRowPanel(DropRow row)
	{
		JPanel rowPanel = new JPanel();
		rowPanel.setLayout(new BoxLayout(rowPanel, BoxLayout.Y_AXIS));
		rowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		rowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
		rowPanel.setBackground(TABLE_ROW);
		rowPanel.setBorder(BorderFactory.createMatteBorder(0, 1, 1, 1, DIVIDER));

		String rate = simplifyRarity(row.getRarity());
		JLabel itemLabel = createCell(row.getItem(), TABLE_ROW, Font.BOLD);
		itemLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		rowPanel.add(itemLabel);

		JLabel detailLabel = createCell("Qty " + displayText(row.getQuantity())
			+ "  |  Rate " + displayText(rate)
			+ "  |  Price " + displayText(row.getPrice()), rarityColor(row.getRarityScore()), Font.PLAIN);
		detailLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		rowPanel.add(detailLabel);
		return rowPanel;
	}

	private JLabel createCell(String text, Color background, int fontStyle)
	{
		JLabel label = new JLabel("<html><body style='width:" + ROW_WRAP_WIDTH + "px'>" + escape(displayText(text)) + "</body></html>");
		label.setOpaque(true);
		label.setBackground(background);
		label.setForeground(TEXT);
		label.setFont(label.getFont().deriveFont(fontStyle, META_FONT_SIZE));
		label.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
		return label;
	}

	private JLabel createMessageLabel(String text)
	{
		JLabel label = new JLabel("<html><body style='width:180px'>" + escape(text) + "</body></html>");
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setForeground(MUTED);
		label.setFont(label.getFont().deriveFont(Font.PLAIN, META_FONT_SIZE));
		return label;
	}

	private void renderEmptyState()
	{
		resultsPanel.removeAll();
		resultsPanel.add(createMessageLabel("Search for a monster to load its wiki drop table. Suggestions appear as you type."));
		repaintResults();
	}

	private void requestSuggestions()
	{
		if (applyingSuggestion)
		{
			return;
		}

		String query = searchField.getText().trim();
		if (query.length() < 2)
		{
			clearSuggestions();
			return;
		}

		executor.submit(() -> suggestionAction.accept(query));
	}

	private void applySelectedSuggestion()
	{
		String selected = suggestionList.getSelectedValue();
		if (selected == null || selected.isBlank())
		{
			return;
		}

		applyingSuggestion = true;
		try
		{
			clearSuggestions();
			searchField.setText(selected);
		}
		finally
		{
			applyingSuggestion = false;
		}
		submitSearch(null);
	}

	private void clearSuggestions()
	{
		suggestionPopup.setVisible(false);
		suggestionModel.clear();
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

	private void repaintResults()
	{
		resultsPanel.revalidate();
		resultsPanel.repaint();
	}

	private String simplifyRarity(String rarity)
	{
		if (rarity == null || rarity.isBlank())
		{
			return "";
		}
		String normalized = rarity.replace(" ", "");
		int start = normalized.indexOf("1/");
		if (start >= 0)
		{
			int end = start + 2;
			while (end < normalized.length())
			{
				char ch = normalized.charAt(end);
				if (!(Character.isDigit(ch) || ch == '.' || ch == ',' || ch == 'k' || ch == 'm' || ch == 'K' || ch == 'M'))
				{
					break;
				}
				end++;
			}
			return normalized.substring(start, end);
		}
		if (normalized.equalsIgnoreCase("Always"))
		{
			return "Always";
		}
		return rarity;
	}

	private Color rarityColor(double rarityScore)
	{
		if (rarityScore <= 0d || rarityScore <= 25d)
		{
			return RATE_COMMON;
		}
		if (rarityScore <= 64d)
		{
			return RATE_UNCOMMON;
		}
		return RATE_RARE;
	}

	private String displayText(String text)
	{
		return text == null || text.isBlank() ? "-" : text;
	}

	private String escape(String text)
	{
		return text
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;");
	}
}
