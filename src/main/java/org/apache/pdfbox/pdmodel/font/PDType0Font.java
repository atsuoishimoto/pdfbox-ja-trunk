/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pdfbox.pdmodel.font;

import java.io.IOException;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This is implementation of the Type0 Font. Note that currently
 * this class simply falls back to the Type1 font implementation
 * when drawing text.
 * See <a href="https://issues.apache.org/jira/browse/PDFBOX-605">PDFBOX-605</a>
 * for the related improvement issue.
 *
 * @author <a href="mailto:ben@benlitchfield.com">Ben Litchfield</a>
 * @version $Revision: 1.9 $
 */
public class PDType0Font extends /*PDFont following is a hack ...*/ PDType1Font
{

    private static final Log log = LogFactory.getLog(PDType0Font.class);
    private static double BOLD_WEIGHT_THRESHOLD = 500.0;
    private PDFont descendentFont;
     private Font awtFont;

    /**
     * Constructor.
     */
    public PDType0Font()
    {
        super();
        font.setItem( COSName.SUBTYPE, COSName.TYPE0 );
    }

    /**
     * Constructor.
     *
     * @param fontDictionary The font dictionary according to the PDF specification.
     */
    public PDType0Font( COSDictionary fontDictionary )
    {
        super( fontDictionary );
    }

    /**
     * {@inheritDoc}
     */
    public void drawString( String string, Graphics g, float fontSize, AffineTransform at, float x, float y ) 
        throws IOException
    {
        Graphics2D g2d = (Graphics2D)g;
        g2d.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
        writeFont(g2d, at, getAwtFont(), fontSize, x, y, string);
    }

    /**
     * This will get the fonts bouding box.
     *
     * @return The fonts bouding box.
     *
     * @throws IOException If there is an error getting the bounding box.
     */
    public PDRectangle getFontBoundingBox() throws IOException
    {
        throw new RuntimeException( "Not yet implemented" );
    }

    /**
     * This will get the font width for a character.
     *
     * @param c The character code to get the width for.
     * @param offset The offset into the array.
     * @param length The length of the data.
     *
     * @return The width is in 1000 unit of text space, ie 333 or 777
     *
     * @throws IOException If an error occurs while parsing.
     */
    public float getFontWidth( byte[] c, int offset, int length ) throws IOException
    {
        if (descendentFont == null) 
        {
            COSArray descendantFontArray =
                (COSArray)font.getDictionaryObject( COSName.DESCENDANT_FONTS );
            
            COSDictionary descendantFontDictionary = (COSDictionary)descendantFontArray.getObject( 0 );
            descendentFont = PDFontFactory.createFont( descendantFontDictionary );
        }
        return descendentFont.getFontWidth( c, offset, length );
    }

    /**
     * This will get the font height for a character.
     *
     * @param c The character code to get the height for.
     * @param offset The offset into the array.
     * @param length The length of the data.
     *
     * @return The width is in 1000 unit of text space, ie 333 or 777
     *
     * @throws IOException If an error occurs while parsing.
     */
    public float getFontHeight( byte[] c, int offset, int length ) throws IOException
    {
        if (descendentFont == null) 
        {
            COSArray descendantFontArray =
                (COSArray)font.getDictionaryObject( COSName.DESCENDANT_FONTS );
            
            COSDictionary descendantFontDictionary = (COSDictionary)descendantFontArray.getObject( 0 );
            descendentFont = PDFontFactory.createFont( descendantFontDictionary );
        }
        return descendentFont.getFontHeight( c, offset, length );
    }

    /**
     * This will get the average font width for all characters.
     *
     * @return The width is in 1000 unit of text space, ie 333 or 777
     *
     * @throws IOException If an error occurs while parsing.
     */
    public float getAverageFontWidth() throws IOException
    {
        if (descendentFont == null) 
        {
            COSArray descendantFontArray =
                (COSArray)font.getDictionaryObject( COSName.DESCENDANT_FONTS );
            
            COSDictionary descendantFontDictionary = (COSDictionary)descendantFontArray.getObject( 0 );
            descendentFont = PDFontFactory.createFont( descendantFontDictionary );
        }
        return descendentFont.getAverageFontWidth();
    }

    private Font getAwtFont() throws IOException {
        if (awtFont != null) {
            return awtFont;
        }

        PDFontDescriptor fd = null;
        if (descendentFont instanceof PDCIDFont) {
            fd = ((PDCIDFont)descendentFont).getFontDescriptor();
        } else if (descendentFont instanceof PDSimpleFont) {
            fd = ((PDSimpleFont)descendentFont).getFontDescriptor();
        }

        if (fd != null) {
            if (fd instanceof PDFontDescriptorDictionary) {
                PDFontDescriptorDictionary fdd = (PDFontDescriptorDictionary)fd;

                PDStream ffStream = null;
                if ((ffStream = fdd.getFontFile()) != null) {
                    try {
                        awtFont = Font.createFont( Font.TYPE1_FONT, ffStream.createInputStream() );
                    } catch (FontFormatException e) {
                        // ignore
                    }
                } else if ((ffStream = fdd.getFontFile2()) != null) {
                    try {
                        awtFont = Font.createFont( Font.TRUETYPE_FONT, ffStream.createInputStream() );
                    } catch (FontFormatException e) {
                        // ignore
                    }
                } else if ((ffStream = fdd.getFontFile3()) != null) {
                    log.info("font3");
                }
            }
            if (awtFont == null) {
                awtFont = FontManager.getAwtFont(fd.getFontName());
            }
        }
        if (awtFont == null) {
            awtFont = FontManager.getAwtFont(getBaseFont());
        }
        if (awtFont == null) {
            awtFont = FontManager.getStandardFont();
            if (fd != null) {
                int style = Font.PLAIN;
                if (fd.getFontWeight() >= BOLD_WEIGHT_THRESHOLD || fd.isForceBold()) {
                    style |= Font.BOLD;
                }
                if (fd.isItalic()) {
                    style |= Font.ITALIC;
                }
                if (style != Font.PLAIN) {
                    awtFont = new Font(awtFont.getName(), style, awtFont.getSize());
                }
            }
        }
        return awtFont;
    }
}
