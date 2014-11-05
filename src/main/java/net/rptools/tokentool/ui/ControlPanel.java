package net.rptools.tokentool.ui;

import java.awt.EventQueue;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.rptools.lib.swing.ImageToggleButton;
import net.rptools.tokentool.AppState;
import net.rptools.tokentool.TokenTool;

import com.jeta.forms.components.panel.FormPanel;

public class ControlPanel extends JPanel {

	private JSpinner widthSpinner;
	private JSpinner heightSpinner;
	private JSlider transparencySlider;
	private JToggleButton lockToggleButton;
	private JComboBox overlayCombo;
	private JButton zoomOutButton;
	private JButton zoomInButton;
	private JButton zoomOutFastButton;
	private JButton zoomInFastButton;
	private JCheckBox solidBackgroundCheckBox;
	private JCheckBox baseCheckBox;
	private JSlider fudgeSlider;
	private JLabel fudgeValueLabel;
	private JLabel transparencyValueLabel;
	private JComboBox sizesCombo; // Combo box to pick from 6 pre-defined sizes
	private JTextField overlayHeightField; // Display the selected overlay's height
	private JTextField overlayWidthField; // Display the selected overlay's width

	private FormPanel formPanel;
	
	public ControlPanel() {
		setLayout(new GridLayout());
		
		formPanel = new FormPanel("net/rptools/tokentool/forms/controlPanel.jfrm");

		getWidthSpinner();
		getHeightSpinner();
		getTransparencySlider();
		getOverlayCombo();
		getStockSizesCombo();
		getZoomOutButton();
		getZoomInButton();
		getZoomOutFastButton();
		getZoomInFastButton();
		getSolidBackgroundCheckBox().setSelected(true);
		getBaseCheckBox();
		getFudgeFactorSlider();
		formPanel.getFormAccessor().replaceBean("lockToggle", getLockToggle());
		getOverlayWidthField();
		getOverlayHeightField();
		updateLabels();
		
		add(formPanel);
	}

	public JLabel getTransparencyValueLabel() {
		if (transparencyValueLabel == null) {
			transparencyValueLabel = formPanel.getLabel("transparencyValue");
		}
		return transparencyValueLabel;
	}
	
	public JLabel getFudgeValueLabel() {
		if (fudgeValueLabel == null) {
			fudgeValueLabel = formPanel.getLabel("fudgeValue");
		}
		return fudgeValueLabel;
	}
	
	public JSpinner getWidthSpinner() {
		if (widthSpinner == null) {
			widthSpinner = formPanel.getSpinner("width");
			widthSpinner.setValue(128);
			widthSpinner.addChangeListener(new ChangeListener(){
				public void stateChanged(ChangeEvent e) {
					updateWidth();
				}
			});
		}
		return widthSpinner;
	}
	private void updateWidth() {
		if (getLockToggle().isSelected()) {
			heightSpinner.setValue(widthSpinner.getValue());
		}

		TokenTool.getFrame().getTokenCompositionPanel().repaint();
	}
	public JSpinner getHeightSpinner() {
		if (heightSpinner == null) {
			heightSpinner = formPanel.getSpinner("height");
			heightSpinner.setValue(128);
			heightSpinner.addChangeListener(new ChangeListener(){
				public void stateChanged(ChangeEvent e) {
					updateHeight();
				}
			});
		}
		return heightSpinner;
	}
	private void updateHeight() {
		if (getLockToggle().isSelected()) {
			widthSpinner.setValue(heightSpinner.getValue());
		}

		TokenTool.getFrame().getTokenCompositionPanel().repaint();
	}
	
	private void updateLabels() {
		
		getTransparencyValueLabel().setText(getTransparencySlider().getValue() + "%");
		getFudgeValueLabel().setText("+/- " + getFudgeFactorSlider().getValue());
	}
	
	public JSlider getTransparencySlider() {
		if (transparencySlider == null) {
			transparencySlider = (JSlider)formPanel.getComponentByName("transparency");
			transparencySlider.addChangeListener(new ChangeListener() {
				public void stateChanged(ChangeEvent e) {

					AppState.compositionProperties.setTranslucency(transparencySlider.getValue()/100.0);

					updateLabels();

					TokenTool.getFrame().getTokenCompositionPanel().fireCompositionChanged();
				}
			});
		}
		return transparencySlider;
	}
	public JSlider getFudgeFactorSlider() {
		if (fudgeSlider == null) {
			fudgeSlider = (JSlider)formPanel.getComponentByName("fudgeFactor");
			fudgeSlider.addChangeListener(new ChangeListener() {
				public void stateChanged(ChangeEvent e) {

					AppState.compositionProperties.setFudgeFactor(fudgeSlider.getValue());
					
					updateLabels();

					TokenTool.getFrame().getTokenCompositionPanel().fireCompositionChanged();
				}
			});
		}
		return fudgeSlider;
	}
	public JButton getZoomOutButton() {
		if (zoomOutButton == null) {
			zoomOutButton = (JButton) formPanel.getFormAccessor("zoomPanel").getComponentByName("zoomOut");
			zoomOutButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					TokenTool.getFrame().getTokenCompositionPanel().zoomOut();
				}
			});
		}
			
		return zoomOutButton;
	}
	public JButton getZoomInButton() {
		if (zoomInButton == null) {
			zoomInButton = (JButton) formPanel.getFormAccessor("zoomPanel").getComponentByName("zoomIn");
			zoomInButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					TokenTool.getFrame().getTokenCompositionPanel().zoomIn();
				}
			});
		}
			
		return zoomInButton;
	}
	public JButton getZoomOutFastButton() {
		if (zoomOutFastButton == null) {
			zoomOutFastButton = (JButton) formPanel.getFormAccessor("zoomPanel").getComponentByName("zoomOutFast");
			zoomOutFastButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					TokenTool.getFrame().getTokenCompositionPanel().zoomOutFast();
				}
			});
		}
			
		return zoomOutFastButton;
	}
	public JButton getZoomInFastButton() {
		if (zoomInFastButton == null) {
			zoomInFastButton = (JButton) formPanel.getFormAccessor("zoomPanel").getComponentByName("zoomInFast");
			zoomInFastButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					TokenTool.getFrame().getTokenCompositionPanel().zoomInFast();
				}
			});
		}
			
		return zoomInFastButton;
	}
	public JComboBox getOverlayCombo() {
		if (overlayCombo == null) {
			overlayCombo = formPanel.getComboBox("overlay");
			overlayCombo.setModel(new OverlayListModel());
			overlayCombo.setRenderer(new OverlayListRenderer());
			overlayCombo.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					BufferedImage overlay = ((OverlayListModel)overlayCombo.getModel()).getSelectedOverlay();
					TokenTool.getFrame().getTokenCompositionPanel().setOverlay(overlay);
					overlayWidthField.setText(Integer.toString(overlay.getWidth()));
					overlayHeightField.setText(Integer.toString(overlay.getHeight()));
				}
			});
			EventQueue.invokeLater(new Runnable() {
				public void run() {
					// Do this AFTER the UI has finished being built
					overlayCombo.setSelectedIndex(0);
				}
			});
		}
		return overlayCombo;
	}
	public JComboBox getStockSizesCombo(){
		if (sizesCombo == null) {
			sizesCombo = formPanel.getComboBox("stockSizes");
			if (sizesCombo != null){
				sizesCombo.setSelectedIndex(3);
				
				sizesCombo.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						int size = sizesCombo.getSelectedIndex();
						// PW: Default sizes were put into the combobox 
						// in the form.
						switch (size){
							case 0:
								heightSpinner.setValue(256);
								widthSpinner.setValue(256);
								break;
							case 1:
								heightSpinner.setValue(200);
								widthSpinner.setValue(200);
								break;
							case 2:
								heightSpinner.setValue(160);
								widthSpinner.setValue(160);
								break;
							case 3:
								heightSpinner.setValue(128);
								widthSpinner.setValue(128);
								break;
							case 4:
								heightSpinner.setValue(80);
								widthSpinner.setValue(80);
								break;
							case 5:
								heightSpinner.setValue(64);
								widthSpinner.setValue(64);
								break;
							default:
								heightSpinner.setValue(128);
								widthSpinner.setValue(128);
								break;
						}
						TokenTool.getFrame().getTokenCompositionPanel().repaint();
					}
				});
			}
		}
		return sizesCombo;
	}
	public JTextField getOverlayWidthField(){
		if (overlayWidthField == null) {
			overlayWidthField = formPanel.getTextField("overlayWidth");
			overlayWidthField.setText("128");
		}
		return overlayWidthField;
	}
	public JTextField getOverlayHeightField(){
		if (overlayHeightField == null) {
			overlayHeightField = formPanel.getTextField("overlayHeight");
			overlayHeightField.setText("128");
		}
		return overlayHeightField;
	}
	public JToggleButton getLockToggle() {
		if (lockToggleButton == null) {
			lockToggleButton = new ImageToggleButton("net/rptools/tokentool/image/locked.png", "net/rptools/tokentool/image/unlocked.png");
			lockToggleButton.setSelected(true);
		}
		return lockToggleButton;
	}
	public JCheckBox getSolidBackgroundCheckBox() {
		if (solidBackgroundCheckBox == null) {
			solidBackgroundCheckBox = formPanel.getCheckBox("solidBackground");
			solidBackgroundCheckBox.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					AppState.compositionProperties.setSolidBackground(solidBackgroundCheckBox.isSelected());
					TokenTool.getFrame().getTokenCompositionPanel().fireCompositionChanged();
				}
			});
		}
		return solidBackgroundCheckBox;
	}

	public JCheckBox getBaseCheckBox() {
		if (baseCheckBox == null) {
			baseCheckBox = formPanel.getCheckBox("base");
			baseCheckBox.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					AppState.compositionProperties.setBase(baseCheckBox.isSelected());
					TokenTool.getFrame().getTokenCompositionPanel().fireCompositionChanged();
					TokenTool.getFrame().getTokenCompositionPanel().repaint();
				}
			});
		}
		return baseCheckBox;
	}
}
