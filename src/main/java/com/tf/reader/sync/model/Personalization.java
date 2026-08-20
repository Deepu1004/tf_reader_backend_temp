package com.tf.reader.sync.model;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Reader appearance settings. Scoped to the user only - there is no bookId, and no
 * separate preferences collection.
 */
@Document(collection = "personalization")
@CompoundIndex(name = "personalization_user_uk", def = "{'userId': 1}", unique = true)
public class Personalization extends BaseDocument {

    private String theme = "system";

    private String fontFamily = "system";

    private String customFontUri;

    private Double typographySize = 1.0;

    private Double typographyLineHeight = 1.5;

    private Double typographySpacing = 0.0;

    private Double typographyMargins = 16.0;

    private String layoutFlow = "paginated";

    private String layoutSpread = "single";

    private Double zoom = 1.0;

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getFontFamily() {
        return fontFamily;
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
    }

    public String getCustomFontUri() {
        return customFontUri;
    }

    public void setCustomFontUri(String customFontUri) {
        this.customFontUri = customFontUri;
    }

    public Double getTypographySize() {
        return typographySize;
    }

    public void setTypographySize(Double typographySize) {
        this.typographySize = typographySize;
    }

    public Double getTypographyLineHeight() {
        return typographyLineHeight;
    }

    public void setTypographyLineHeight(Double typographyLineHeight) {
        this.typographyLineHeight = typographyLineHeight;
    }

    public Double getTypographySpacing() {
        return typographySpacing;
    }

    public void setTypographySpacing(Double typographySpacing) {
        this.typographySpacing = typographySpacing;
    }

    public Double getTypographyMargins() {
        return typographyMargins;
    }

    public void setTypographyMargins(Double typographyMargins) {
        this.typographyMargins = typographyMargins;
    }

    public String getLayoutFlow() {
        return layoutFlow;
    }

    public void setLayoutFlow(String layoutFlow) {
        this.layoutFlow = layoutFlow;
    }

    public String getLayoutSpread() {
        return layoutSpread;
    }

    public void setLayoutSpread(String layoutSpread) {
        this.layoutSpread = layoutSpread;
    }

    public Double getZoom() {
        return zoom;
    }

    public void setZoom(Double zoom) {
        this.zoom = zoom;
    }
}
