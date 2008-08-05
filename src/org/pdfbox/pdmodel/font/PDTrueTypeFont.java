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
package org.pdfbox.pdmodel.font;

import org.fontbox.ttf.CMAPEncodingEntry;
import org.fontbox.ttf.CMAPTable;
import org.fontbox.ttf.GlyphData;
import org.fontbox.ttf.GlyphTable;
import org.fontbox.ttf.HeaderTable;
import org.fontbox.ttf.HorizontalHeaderTable;
import org.fontbox.ttf.HorizontalMetricsTable;
import org.fontbox.ttf.NamingTable;
import org.fontbox.ttf.NameRecord;
import org.fontbox.ttf.OS2WindowsMetricsTable;
import org.fontbox.ttf.PostScriptTable;
import org.fontbox.ttf.TTFParser;
import org.fontbox.ttf.TrueTypeFont;

import org.pdfbox.cos.COSDictionary;
import org.pdfbox.cos.COSName;

import org.pdfbox.pdmodel.PDDocument;

import org.pdfbox.pdmodel.common.PDRectangle;
import org.pdfbox.pdmodel.common.PDStream;

import org.pdfbox.encoding.WinAnsiEncoding;
import org.pdfbox.exceptions.WrappedIOException;

import org.pdfbox.util.ResourceLoader;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * This is the TrueType implementation of fonts.
 *
 * @author <a href="mailto:ben@benlitchfield.com">Ben Litchfield</a>
 * @version $Revision: 1.17 $
 */
public class PDTrueTypeFont extends PDSimpleFont
{
    /**
     * This is the key to a property in the Resources/PDFBox_External_Fonts.properties file
     * to load a Font when a mapping does not exist for the current font.
     */
    public static final String UNKNOWN_FONT = "UNKNOWN_FONT";

    private Font awtFont = null;

    private static Properties externalFonts = new Properties();
    private static Map loadedExternalFonts = new HashMap();

    static
    {
        try
        {
            ResourceLoader.loadProperties( "Resources/PDFBox_External_Fonts.properties", externalFonts );
        }
        catch( IOException io )
        {
            io.printStackTrace();
            throw new RuntimeException( "Error loading font resources" );
        }
    }


    /**
     * Constructor.
     */
    public PDTrueTypeFont()
    {
        super();
        font.setItem( COSName.SUBTYPE, COSName.TRUE_TYPE );
    }

    /**
     * Constructor.
     *
     * @param fontDictionary The font dictionary according to the PDF specification.
     */
    public PDTrueTypeFont( COSDictionary fontDictionary )
    {
        super( fontDictionary );
    }

    /**
     * This will load a TTF font from a font file.
     *
     * @param doc The PDF document that will hold the embedded font.
     * @param file The file on the filesystem that holds the font file.
     * @return A true type font.
     * @throws IOException If there is an error loading the file data.
     */
    public static PDTrueTypeFont loadTTF( PDDocument doc, String file ) throws IOException
    {
        return loadTTF( doc, new File( file ) );
    }

    /**
     * This will load a TTF to be embedding into a document.
     *
     * @param doc The PDF document that will hold the embedded font.
     * @param file A TTF file stream.
     * @return A PDF TTF.
     * @throws IOException If there is an error loading the data.
     */
    public static PDTrueTypeFont loadTTF( PDDocument doc, File file ) throws IOException
    {
        PDTrueTypeFont retval = new PDTrueTypeFont();
        PDFontDescriptorDictionary fd = new PDFontDescriptorDictionary();
        PDStream fontStream = new PDStream(doc, new FileInputStream( file ), false );
        fontStream.getStream().setInt( COSName.LENGTH1, (int)file.length() );
        fontStream.addCompression();
        fd.setFontFile2( fontStream );
        retval.setFontDescriptor( fd );
        //only support winansi encoding right now, should really
        //just use Identity-H with unicode mapping
        retval.setEncoding( new WinAnsiEncoding() );
        TrueTypeFont ttf = null;
        try
        {
            TTFParser parser = new TTFParser();
            ttf = parser.parseTTF( file );
            NamingTable naming = ttf.getNaming();
            List records = naming.getNameRecords();
            for( int i=0; i<records.size(); i++ )
            {
                NameRecord nr = (NameRecord)records.get( i );
                if( nr.getNameId() == NameRecord.NAME_POSTSCRIPT_NAME )
                {
                    retval.setBaseFont( nr.getString() );
                    fd.setFontName( nr.getString() );
                }
                else if( nr.getNameId() == NameRecord.NAME_FONT_FAMILY_NAME )
                {
                    fd.setFontFamily( nr.getString() );
                }
            }

            OS2WindowsMetricsTable os2 = ttf.getOS2Windows();
            fd.setNonSymbolic( true );
            switch( os2.getFamilyClass() )
            {
                case OS2WindowsMetricsTable.FAMILY_CLASS_SYMBOLIC:
                    fd.setSymbolic( true );
                    fd.setNonSymbolic( false );
                    break;
                case OS2WindowsMetricsTable.FAMILY_CLASS_SCRIPTS:
                    fd.setScript( true );
                    break;
                case OS2WindowsMetricsTable.FAMILY_CLASS_CLAREDON_SERIFS:
                case OS2WindowsMetricsTable.FAMILY_CLASS_FREEFORM_SERIFS:
                case OS2WindowsMetricsTable.FAMILY_CLASS_MODERN_SERIFS:
                case OS2WindowsMetricsTable.FAMILY_CLASS_OLDSTYLE_SERIFS:
                case OS2WindowsMetricsTable.FAMILY_CLASS_SLAB_SERIFS:
                    fd.setSerif( true );
                    break;
                default:
                    //do nothing
            }
            switch( os2.getWidthClass() )
            {
                case OS2WindowsMetricsTable.WIDTH_CLASS_ULTRA_CONDENSED:
                    fd.setFontStretch( "UltraCondensed" );
                    break;
                case OS2WindowsMetricsTable.WIDTH_CLASS_EXTRA_CONDENSED:
                    fd.setFontStretch( "ExtraCondensed" );
                    break;
                case OS2WindowsMetricsTable.WIDTH_CLASS_CONDENSED:
                    fd.setFontStretch( "Condensed" );
                    break;
                case OS2WindowsMetricsTable.WIDTH_CLASS_SEMI_CONDENSED:
                    fd.setFontStretch( "SemiCondensed" );
                    break;
                case OS2WindowsMetricsTable.WIDTH_CLASS_MEDIUM:
                    fd.setFontStretch( "Normal" );
                    break;
                case OS2WindowsMetricsTable.WIDTH_CLASS_SEMI_EXPANDED:
                    fd.setFontStretch( "SemiExpanded" );
                    break;
                case OS2WindowsMetricsTable.WIDTH_CLASS_EXPANDED:
                    fd.setFontStretch( "Expanded" );
                    break;
                case OS2WindowsMetricsTable.WIDTH_CLASS_EXTRA_EXPANDED:
                    fd.setFontStretch( "ExtraExpanded" );
                    break;
                case OS2WindowsMetricsTable.WIDTH_CLASS_ULTRA_EXPANDED:
                    fd.setFontStretch( "UltraExpanded" );
                    break;
                default:
                    //do nothing
            }
            fd.setFontWeight( os2.getWeightClass() );

            //todo retval.setFixedPitch
            //todo retval.setNonSymbolic
            //todo retval.setItalic
            //todo retval.setAllCap
            //todo retval.setSmallCap
            //todo retval.setForceBold

            HeaderTable header = ttf.getHeader();
            PDRectangle rect = new PDRectangle();
            rect.setLowerLeftX( header.getXMin() * 1000f/header.getUnitsPerEm() );
            rect.setLowerLeftY( header.getYMin() * 1000f/header.getUnitsPerEm() );
            rect.setUpperRightX( header.getXMax() * 1000f/header.getUnitsPerEm() );
            rect.setUpperRightY( header.getYMax() * 1000f/header.getUnitsPerEm() );
            fd.setFontBoundingBox( rect );

            HorizontalHeaderTable hHeader = ttf.getHorizontalHeader();
            fd.setAscent( hHeader.getAscender() * 1000f/header.getUnitsPerEm() );
            fd.setDescent( hHeader.getDescender() * 1000f/header.getUnitsPerEm() );

            GlyphTable glyphTable = ttf.getGlyph();
            GlyphData[] glyphs = glyphTable.getGlyphs();

            PostScriptTable ps = ttf.getPostScript();
            fd.setFixedPitch( ps.getIsFixedPitch() > 0 );
            fd.setItalicAngle( ps.getItalicAngle() );

            String[] names = ps.getGlyphNames();
            if( names != null )
            {
                for( int i=0; i<names.length; i++ )
                {
                    //if we have a capital H then use that, otherwise use the
                    //tallest letter
                    if( names[i].equals( "H" ) )
                    {
                        fd.setCapHeight( (glyphs[i].getBoundingBox().getUpperRightY()* 1000f)/
                                         header.getUnitsPerEm() );
                    }
                    if( names[i].equals( "x" ) )
                    {
                        fd.setXHeight( (glyphs[i].getBoundingBox().getUpperRightY()* 1000f)/header.getUnitsPerEm() );
                    }
                }
            }

            //hmm there does not seem to be a clear definition for StemV,
            //this is close enough and I am told it doesn't usually get used.
            fd.setStemV( (fd.getFontBoundingBox().getWidth() * .13f) );


            CMAPTable cmapTable = ttf.getCMAP();
            CMAPEncodingEntry[] cmaps = cmapTable.getCmaps();
            int[] glyphToCCode = null;
            for( int i=0; i<cmaps.length; i++ )
            {
                if( cmaps[i].getPlatformId() == CMAPTable.PLATFORM_WINDOWS &&
                    cmaps[i].getPlatformEncodingId() == CMAPTable.ENCODING_UNICODE )
                {
                    glyphToCCode = cmaps[i].getGlyphIdToCharacterCode();
                }
            }
            int firstChar = 0;
            int maxWidths=256;
            HorizontalMetricsTable hMet = ttf.getHorizontalMetrics();
            int[] widthValues = hMet.getAdvanceWidth();
            List widths = new ArrayList(maxWidths);
            Integer zero = new Integer( 250 );
            for( int i=0; i<maxWidths; i++ )
            {
                widths.add( zero );
            }
            for( int i=0; i<widthValues.length; i++ )
            {
                if(glyphToCCode[i]-firstChar < widths.size() && glyphToCCode[i]-firstChar >= 0 && widths.get( glyphToCCode[i]-firstChar) == zero )
                {
                    widths.set( glyphToCCode[i]-firstChar,
                        new Integer( (int)(widthValues[i]* 1000f)/header.getUnitsPerEm() ) );
                }
            }
            retval.setWidths( widths );
            retval.setFirstChar( firstChar );
            retval.setLastChar( firstChar + widths.size()-1 );

        }
        finally
        {
            if( ttf != null )
            {
                ttf.close();
            }
        }

        return retval;
    }

    /**
     * {@inheritDoc}
     */
    public void drawString( String string, Graphics g, float fontSize,
        float xScale, float yScale, float x, float y ) throws IOException
    {
        PDFontDescriptorDictionary fd = (PDFontDescriptorDictionary)getFontDescriptor();
        if( awtFont == null )
        {
            try
            {
                PDStream ff2Stream = fd.getFontFile2();
                String fontName = fd.getFontName();
                if( ff2Stream != null )
                {
                    awtFont = Font.createFont( Font.TRUETYPE_FONT, ff2Stream.createInputStream() );
                }
                else
                {
                    //throw new IOException( "Error:TTF Stream is null");
                    // Embedded true type programs are optional,
                    // if there is no stream, we must use an external
                    // file.
                    TrueTypeFont ttf = getExternalFontFile2( fd );
                    if( ttf != null )
                    {
                        awtFont = Font.createFont( Font.TRUETYPE_FONT, ttf.getOriginalData() );
                    }
                    else
                    {
                        awtFont = Font.getFont( fontName, null );
                    }
                }
            }
            catch( FontFormatException f )
            {
                throw new WrappedIOException( f );
            }
        }
        AffineTransform at = new AffineTransform();
        at.scale( xScale, yScale );
        Graphics2D g2d = (Graphics2D)g;
        g2d.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
        g2d.setFont( awtFont.deriveFont( at ).deriveFont( fontSize ) );
        g2d.drawString( string, (int)x, (int)y );
    }

    /**
     * Permit to load an external TTF Font program file
     *
     * Created by Pascal Allain
     * Vertical7 Inc.
     *
     * @param fd The font descriptor currently used
     *
     * @return A PDStream with the Font File program, null if fd is null
     *
     * @throws IOException If the font is not found
     */
    private TrueTypeFont getExternalFontFile2(PDFontDescriptorDictionary fd)
        throws IOException
    {
        TrueTypeFont retval = null;

        if ( fd != null )
        {
            String baseFont = getBaseFont();
            String fontResource = externalFonts.getProperty( UNKNOWN_FONT );
            if( (baseFont != null) &&
                 (externalFonts.containsKey(baseFont)) )
            {
                fontResource = externalFonts.getProperty(baseFont);
            }
            if( fontResource != null )
            {
                retval = (TrueTypeFont)loadedExternalFonts.get( baseFont );
                if( retval == null )
                {
                    TTFParser ttfParser = new TTFParser();
                    InputStream fontStream = ResourceLoader.loadResource( fontResource );
                    if( fontStream == null )
                    {
                        throw new IOException( "Error missing font resource '" + externalFonts.get(baseFont) + "'" );
                    }
                    retval = ttfParser.parseTTF( fontStream );
                    loadedExternalFonts.put( baseFont, retval );
                }
            }
        }

        return retval;
    }
}