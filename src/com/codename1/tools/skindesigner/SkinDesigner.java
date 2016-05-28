/*
 * Copyright (c) 2012, Codename One and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Codename One designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *  
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * 
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Please contact Codename One through http://www.codenameone.com/ if you 
 * need additional information or have any questions.
 */
package com.codename1.tools.skindesigner;


import com.codename1.components.FloatingHint;
import com.codename1.components.ImageViewer;
import com.codename1.components.OnOffSwitch;
import com.codename1.components.ScaleImageButton;
import com.codename1.components.ScaleImageLabel;
import com.codename1.components.ToastBar;
import com.codename1.io.FileSystemStorage;
import com.codename1.ui.Display;
import com.codename1.ui.Form;
import com.codename1.ui.Label;
import com.codename1.ui.plaf.UIManager;
import com.codename1.ui.util.Resources;
import com.codename1.io.Log;
import com.codename1.io.Properties;
import com.codename1.io.Storage;
import com.codename1.io.Util;
import com.codename1.system.NativeLookup;
import com.codename1.ui.Button;
import com.codename1.ui.Command;
import com.codename1.ui.Container;
import com.codename1.ui.EncodedImage;
import com.codename1.ui.FontImage;
import com.codename1.ui.Graphics;
import com.codename1.ui.Image;
import com.codename1.ui.Tabs;
import com.codename1.ui.TextField;
import com.codename1.ui.Toolbar;
import com.codename1.ui.events.ActionEvent;
import com.codename1.ui.layouts.BorderLayout;
import com.codename1.ui.layouts.BoxLayout;
import com.codename1.ui.layouts.GridLayout;
import com.codename1.ui.layouts.LayeredLayout;
import com.codename1.ui.plaf.Style;
import com.codename1.ui.spinner.Picker;
import com.codename1.ui.util.ImageIO;
import com.codename1.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import net.sf.zipme.ZipEntry;
import net.sf.zipme.ZipOutputStream;

public class SkinDesigner {
    private static final String[] NATIVE_THEMES = {"iOS 7+", "iOS 6", "Android 4 +","Android 2.x", "Windows"};
    private static final String[] NATIVE_THEME_FILES = {"iOS7Theme.res", "iPhoneTheme.res", 
        "android_holo_light.res","androidTheme.res", "winTheme.res"};
    private Form current;
    private Resources theme;

    public void init(Object context) {
        theme = UIManager.initFirstTheme("/theme");
        Toolbar.setGlobalToolbar(true);
        Log.bindCrashProtection(true);
    }
    
    interface ImageSettings {
        Container getContainer();
        Image createSkinOverlay();
        Image getSkinImage();
    }
    
    private ImageSettings createImageSettings(String imageFile) {
        Image img = null;
        try {
            img = Image.createImage(Display.getInstance().getResourceAsStream(getClass(), imageFile));
        } catch(IOException err) {
            Log.e(err);
        }
        ScaleImageLabel sl = new ScaleImageLabel(img);
        Button imagePicker = new Button("Select Image");
        imagePicker.addActionListener((e) -> {
            Display.getInstance().openGallery((ee) -> {
                    if(ee != null && ee.getSource() != null) {
                        try {
                            sl.setIcon(Image.createImage((String)ee.getSource()));
                            sl.getParent().revalidate();
                        } catch(IOException err) {
                            ToastBar.showErrorMessage("Error Loading Image: " + err);
                        }
                    }
                }, Display.GALLERY_IMAGE);
        });
        
        final TextField screenWidthPixels = new TextField("320", "Width", 8, TextField.NUMERIC);
        final TextField screenHeightPixels = new TextField("480", "Height", 8, TextField.NUMERIC);
        final TextField screenPositionX = new TextField("40", "X", 8, TextField.NUMERIC);
        final TextField screenPositionY = new TextField("40", "Y", 8, TextField.NUMERIC);
        
        Button aim = new Button();
        FontImage.setMaterialIcon(aim, FontImage.MATERIAL_PAN_TOOL);
        
        aim.addActionListener(e -> 
                aimPosition(sl.getIcon(), 
                        screenPositionX, 
                        screenPositionY, 
                        screenWidthPixels.getAsInt(768), 
                        screenHeightPixels.getAsInt(1024)));
        
        final Container cnt = BoxLayout.encloseY(imagePicker,
                BorderLayout.center(
                        new Label("Screen Position (X/Y/Width/Height)")).
                        add(BorderLayout.EAST, aim),
                GridLayout.encloseIn(4, screenPositionX, screenPositionY, screenWidthPixels, screenHeightPixels),
                sl);
        cnt.setScrollableY(true);
        return new ImageSettings() {
            @Override
            public Container getContainer() {
                return cnt;
            }

            @Override
            public Image getSkinImage() {
                return sl.getIcon();
            }
            
            @Override
            public Image createSkinOverlay() {
                Image skinImage = getSkinImage();
                if(skinImage == null) {
                    return null;
                }
                Image m = Image.createImage(skinImage.getWidth(), skinImage.getHeight(), 0);
                Graphics g = m.getGraphics();
                g.setColor(0);
                g.fillRect(screenPositionX.getAsInt(0), screenPositionY.getAsInt(0), 
                        screenWidthPixels.getAsInt(50), screenHeightPixels.getAsInt(50));
                return m;
            }
        };
    }
    
    private Image createMute(int x, int y, int w, int h, Image img) {
        Image mute = Image.createImage(img.getWidth(), img.getHeight(), 0);
        Graphics g = mute.getGraphics();
        g.setAlpha(150);
        g.setColor(0);
        g.fillRect(x, y, w, h);
        g.setColor(0xff);
        g.drawRect(x, y, w, h);
        g.setAlpha(255);
        return mute;
    }
    
    void aimPosition(final Image img, final TextField x, final TextField y, final int w, final int h) {
        if(img == null) {
            ToastBar.showErrorMessage("You need to pick a skin image first");
            return;
        }
        String originalX = x.getText();
        String originalY = y.getText();
        Form editPosition = new Form("", new BorderLayout());
        Toolbar tb = new Toolbar(true);
        editPosition.setToolbar(tb);
        tb.setUIID("Container");
        tb.addCommandToRightBar("", FontImage.createMaterial(FontImage.MATERIAL_CHECK, "Label", 4), e -> x.getComponentForm().showBack());
        tb.addCommandToLeftBar("", FontImage.createMaterial(FontImage.MATERIAL_CANCEL, "Label", 4), e -> {
            x.setText(originalX);
            y.setText(originalY);
            x.getComponentForm().showBack();
        });

        Image mute = createMute(x.getAsInt(0), y.getAsInt(0), w, h, img);
        class oo extends ImageViewer {
            public oo(Image img) {
                super(img);
            }
            @Override
            public boolean pinch(float scale) {
                return super.pinch(scale); 
            }
        }
        final oo overlay = new oo(mute);
        ImageViewer iv = new ImageViewer(img) {
            @Override
            public void pointerDragged(int x, int y) {
                super.pointerDragged(x, y);
                overlay.pointerDragged(x, y);
            }            
            
            @Override
            protected boolean pinch(float scale) {
                boolean b = super.pinch(scale);
                overlay.pinch(scale);
                return b;
            }
            
            @Override
            public void pointerPressed(int x, int y) {
                super.pointerPressed(x, y);
                overlay.pointerPressed(x, y);
            }

            @Override
            public void pointerReleased(int x, int y) {
                super.pointerReleased(x, y);
                overlay.pointerReleased(x, y);
            }
            
            @Override
            public void keyReleased(int key) {
                super.keyReleased(key); 
                overlay.keyReleased(key);
            }
        };
        overlay.setFocusable(false);
        
        Button zoomIn = new Button();
        Button zoomOut = new Button();
        Button left = new Button();
        Button right = new Button();
        Button up = new Button();
        Button down = new Button();
        FontImage.setMaterialIcon(zoomIn, FontImage.MATERIAL_ZOOM_IN, 3);
        FontImage.setMaterialIcon(zoomOut, FontImage.MATERIAL_ZOOM_OUT, 3);
        FontImage.setMaterialIcon(left, FontImage.MATERIAL_KEYBOARD_ARROW_LEFT, 3);
        FontImage.setMaterialIcon(right, FontImage.MATERIAL_KEYBOARD_ARROW_RIGHT, 3);
        FontImage.setMaterialIcon(up, FontImage.MATERIAL_KEYBOARD_ARROW_UP, 3);
        FontImage.setMaterialIcon(down, FontImage.MATERIAL_KEYBOARD_ARROW_DOWN, 3);
        
        zoomIn.addActionListener(e -> {
            iv.setZoom(iv.getZoom() + 1);
            overlay.setZoom(iv.getZoom());
        });
        
        zoomOut.addActionListener(e -> {
            iv.setZoom(iv.getZoom() - 1);
            overlay.setZoom(iv.getZoom());
        });
        
        left.addActionListener(e -> {
            int newX = x.getAsInt(0) - 1;
            x.setText("" + newX);
            overlay.setImageNoReposition(createMute(x.getAsInt(0), y.getAsInt(0), w, h, img));
        });

        right.addActionListener(e -> {
            int newX = x.getAsInt(0) + 1;
            x.setText("" + newX);
            overlay.setImageNoReposition(createMute(x.getAsInt(0), y.getAsInt(0), w, h, img));
        });

        up.addActionListener(e -> {
            int newY = y.getAsInt(0) - 1;
            y.setText("" + newY);
            overlay.setImageNoReposition(createMute(x.getAsInt(0), y.getAsInt(0), w, h, img));
        });

        down.addActionListener(e -> {
            int newY = y.getAsInt(0) + 1;
            y.setText("" + newY);
            overlay.setImageNoReposition(createMute(x.getAsInt(0), y.getAsInt(0), w, h, img));
        });

        editPosition.add(BorderLayout.CENTER, LayeredLayout.encloseIn(iv, overlay,
            BorderLayout.south(GridLayout.encloseIn(6, zoomIn, zoomOut, left, right, up, down))));
        
        editPosition.show();
    }
    
    private byte[] imageToByteArray(Image img) throws IOException {
        ByteArrayOutputStream bo  = new ByteArrayOutputStream();
        ImageIO.getImageIO().save(img, bo, ImageIO.FORMAT_PNG, 1);
        bo.close();
        return bo.toByteArray();
    }
    
    public void start() {
        if(current != null){
            current.show();
            return;
        }
        Form skinDesignerForm = new Form("Skin Designer", new BorderLayout());
        final Tabs details = new Tabs();
        Style titleCommand = UIManager.getInstance().getComponentStyle("Command");
        ImageSettings imPortrait = createImageSettings("/skin.png");
        ImageSettings imLandscape = createImageSettings("/skin_l.png");
        
        skinDesignerForm.add(BorderLayout.CENTER, details);
        
        Picker nativeTheme = new Picker();
        nativeTheme.setStrings(NATIVE_THEMES);
        nativeTheme.setSelectedString(NATIVE_THEMES[0]);
        nativeTheme.setRenderingPrototype("XXXXXXXXXXXXXXXXXXX");

        Picker platformName = new Picker();
        platformName.setStrings("ios", "and", "win","rim", "se");
        platformName.setSelectedString("ios");
        platformName.setRenderingPrototype("XXXX");

        OnOffSwitch tablet = new OnOffSwitch();
        tablet.setValue(false);

        TextField systemFontFamily = new TextField("Helvetica", "System Font Family", 20, TextField.ANY);
        TextField proportionalFontFamily = new TextField("Helvetica", "Proportional Font Family", 20, TextField.ANY);
        TextField monospaceFontFamily = new TextField("Courier", "Monospace Font Family", 20, TextField.ANY);
        
        TextField smallFontSize = new TextField("11", "Small Font Size", 20, TextField.NUMERIC);
        TextField mediumFontSize = new TextField("14", "Medium Font Size", 20, TextField.NUMERIC);
        TextField largeFontSize = new TextField("20", "Large Font Size", 20, TextField.NUMERIC);
        
        TextField pixelRatio = new TextField("6.4173236936575", "Pixel Ratio - pixels per millimeter", 20, TextField.DECIMAL);
        
        Picker overrideNamePrimary = new Picker();
        overrideNamePrimary.setStrings("phone", "tablet", "desktop");
        overrideNamePrimary.setSelectedString("phone");
        overrideNamePrimary.setRenderingPrototype("XXXXXXXX");

        Picker overrideNameSecondary = new Picker();
        overrideNameSecondary.setStrings("ios", "android", "windows");
        overrideNameSecondary.setSelectedString("ios");
        overrideNameSecondary.setRenderingPrototype("XXXXXXXX");
        
        Picker overrideNameLast = new Picker();
        overrideNameLast.setStrings("iphone", "ipad", "android-phone", "android-tablet", "desktop");
        overrideNameLast.setSelectedString("iphone");
        overrideNameLast.setRenderingPrototype("XXXXXXXX");
        
        
        Container settingsContainer = BoxLayout.encloseY(
                new Label("Native Theme"), 
                nativeTheme,
                new Label("Platform Name"), 
                platformName,
                BorderLayout.center(new Label("Tablet")).add(BorderLayout.EAST, tablet),
                new Label(systemFontFamily.getHint()),
                systemFontFamily,
                new Label(proportionalFontFamily.getHint()),
                proportionalFontFamily,
                new FloatingHint(monospaceFontFamily),
                new Label(smallFontSize.getHint()),
                smallFontSize,
                new Label(mediumFontSize.getHint()),
                mediumFontSize,
                new Label(largeFontSize.getHint()),
                largeFontSize,
                new Label(pixelRatio.getHint()),
                pixelRatio,
                new Label("Platform Overrides"), 
                BoxLayout.encloseX(overrideNamePrimary, overrideNameSecondary, overrideNameLast)
        );
        settingsContainer.setScrollableY(true);
        
        Style tab = UIManager.getInstance().getComponentStyle("Tab");
        Style tabSel = UIManager.getInstance().getComponentSelectedStyle("Tab");
        FontImage portraitIcon = FontImage.createMaterial(FontImage.MATERIAL_STAY_CURRENT_PORTRAIT, tab, 4.5f);
        FontImage landscapeIcon = FontImage.createMaterial(FontImage.MATERIAL_STAY_CURRENT_LANDSCAPE, tab, 4.5f);
        FontImage portraitIconSel = FontImage.createMaterial(FontImage.MATERIAL_STAY_CURRENT_PORTRAIT, tabSel, 4.5f);
        FontImage landscapeIconSel = FontImage.createMaterial(FontImage.MATERIAL_STAY_CURRENT_LANDSCAPE, tabSel, 4.5f);
        FontImage settingsIcon = FontImage.createMaterial(FontImage.MATERIAL_SETTINGS, tab, 3.5f);
        FontImage settingsIconSel = FontImage.createMaterial(FontImage.MATERIAL_SETTINGS, tabSel, 3.5f);
        details.addTab("Portrait", portraitIcon, imPortrait.getContainer());
        details.addTab("Landscape", landscapeIcon, imLandscape.getContainer());
        details.addTab("Settings", settingsIcon, settingsContainer);
        details.setTabSelectedIcon(0, portraitIconSel);
        details.setTabSelectedIcon(1, landscapeIconSel);
        details.setTabSelectedIcon(2, settingsIconSel);
        
        ShouldExecute s = NativeLookup.create(ShouldExecute.class);
        if(s != null && s.isSupported()) {
            skinDesignerForm.getToolbar().addCommandToRightBar("", 
                    FontImage.createMaterial(FontImage.MATERIAL_SAVE, titleCommand), e -> {
                        byte[] data = createSkinFile(imPortrait, imLandscape, nativeTheme, platformName, tablet, systemFontFamily, 
                                proportionalFontFamily, monospaceFontFamily, smallFontSize, mediumFontSize, largeFontSize, 
                                pixelRatio, overrideNamePrimary, overrideNameSecondary, overrideNameLast); 
                        if(data != null) {
                            FileSystemStorage fs = FileSystemStorage.getInstance();
                            try(OutputStream os = fs.openOutputStream(fs.getAppHomePath() + "skin-file.skin")) {
                                os.write(data);
                            } catch(IOException err) {
                                Log.e(err);
                                ToastBar.showErrorMessage("Error wring skin file " + err);
                            }
                            // in the JavaScript port this will trigger the download dialog
                            if(s.shouldExecute()) {
                                Display.getInstance().execute(fs.getAppHomePath() + "skin-file.skin");
                            }
                        }
                    });
        }

        if(Display.getInstance().isNativeShareSupported()) {
            skinDesignerForm.getToolbar().addCommandToRightBar("", 
                    FontImage.createMaterial(FontImage.MATERIAL_SHARE, titleCommand), e -> {
                        byte[] data = createSkinFile(imPortrait, imLandscape, nativeTheme, platformName, tablet, systemFontFamily, 
                                proportionalFontFamily, monospaceFontFamily, smallFontSize, mediumFontSize, largeFontSize, 
                                pixelRatio, overrideNamePrimary, overrideNameSecondary, overrideNameLast); 
                        if(data != null) {
                            FileSystemStorage fs = FileSystemStorage.getInstance();
                            try(OutputStream os = fs.openOutputStream(fs.getAppHomePath() + "skin-file.skin")) {
                                os.write(data);
                            } catch(IOException err) {
                                Log.e(err);
                                ToastBar.showErrorMessage("Error wring skin file " + err);
                            }
                            Display.getInstance().share(null, fs.getAppHomePath() + "skin-file.skin", "application/vnd.codenameone-skin");
                        }
                    });
        }
        
        skinDesignerForm.show();
    }

    byte[] createSkinFile(ImageSettings imPortrait, ImageSettings imLandscape, Picker nativeTheme, Picker platformName, OnOffSwitch tablet, TextField systemFontFamily, TextField proportionalFontFamily, TextField monospaceFontFamily, TextField smallFontSize, TextField mediumFontSize, TextField largeFontSize, TextField pixelRatio, Picker overrideNamePrimary, Picker overrideNameSecondary, Picker overrideNameLast) {
        Image portrait = imPortrait.getSkinImage();
        Image landscape = imLandscape.getSkinImage();
        if (portrait == null) {
            ToastBar.showErrorMessage("Missing portrait skin image");
            return null;
        }
        if (landscape == null) {
            ToastBar.showErrorMessage("Missing landscape skin image");
            return null;
        }
        Image overlayPortrait = imPortrait.createSkinOverlay();
        Image overlayLandscape = imLandscape.createSkinOverlay();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try(ZipOutputStream zos = new ZipOutputStream(bos)) {
            ZipEntry ze = new ZipEntry("skin.png");
            zos.putNextEntry(ze);
            zos.write(imageToByteArray(portrait));
            
            ze = new ZipEntry("skin_l.png");
            zos.putNextEntry(ze);
            zos.write(imageToByteArray(landscape));
            
            ze = new ZipEntry("skin_map.png");
            zos.putNextEntry(ze);
            zos.write(imageToByteArray(overlayPortrait));
            
            ze = new ZipEntry("skin_map_l.png");
            zos.putNextEntry(ze);
            zos.write(imageToByteArray(overlayLandscape));
            
            String theme = nativeTheme.getSelectedString();
            for(int iter = 0 ; iter < NATIVE_THEMES.length ; iter++) {
                if(NATIVE_THEMES[iter].equals(theme)) {
                    ze = new ZipEntry(NATIVE_THEME_FILES[iter]);
                    zos.putNextEntry(ze);
                    InputStream is = Display.getInstance().getResourceAsStream(getClass(), "/" + NATIVE_THEME_FILES[iter]);
                    Util.copyNoClose(is, zos, 8192);
                    break;
                }
            }
            
            Properties props = new Properties();
            props.put("touch", "true");
            props.put("platformName", platformName.getSelectedString());
            props.put("tablet", "" + tablet.isValue());
            props.put("systemFontFamily", systemFontFamily.getText());
            props.put("proportionalFontFamily", proportionalFontFamily.getText());
            props.put("monospaceFontFamily", monospaceFontFamily.getText());
            props.put("smallFontSize", smallFontSize.getText());
            props.put("mediumFontSize", mediumFontSize.getText());
            props.put("largeFontSize", largeFontSize.getText());
            props.put("pixelRatio", pixelRatio.getText());
            props.put("overrideNames", overrideNamePrimary.getSelectedString() + "," +
                    overrideNameSecondary.getSelectedString() + "," +
                    overrideNameLast.getSelectedString());
            
            ze = new ZipEntry("skin.properties");
            zos.putNextEntry(ze);
            props.store(zos, "Created by the Codename One skin designer see https://www.codenameone.com/");
        } catch(IOException err) {
            Log.e(err);
            ToastBar.showErrorMessage("Error while saving file: " + err);
        }
        return bos.toByteArray();
    }

    public void stop() {
        current = Display.getInstance().getCurrent();
    }
    
    public void destroy() {
    }

}
