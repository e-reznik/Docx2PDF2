package docx2pdf;

import com.itextpdf.io.font.FontProgram;
import com.itextpdf.io.font.FontProgramFactory;
import com.itextpdf.io.font.constants.StandardFontFamilies;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import docxjavamapper.model.relationships.DJMRelationships;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class Helper {

    private Helper() {
    }

    private static final Logger LOGGER = LogManager.getLogger(Helper.class);

    /**
     * Returns the stream to a document.xml from the DOCX-archive.
     *
     * @param docx the Docx file
     * @return the InputStream of the document.xml
     * @throws IOException
     */
    public static InputStream getDocument(File docx) throws IOException {
        ZipFile zipFile = new ZipFile(docx);
        return zipFile.getInputStream(zipFile.getEntry("word/document.xml"));
    }

    /**
     * Parses document.xml_rels file inside of the word/_rels directory of the
     * Docx archive. The relationships between some ids (key) and their values
     * are stored there. In this case the target URL of the hyperlink will be
     * found by id.
     *
     * @param docx
     * @param id
     * @return
     * @throws IOException
     */
    public static String getHyperlink(File docx, String id) throws IOException {
        ZipFile zipFile = new ZipFile(docx);
        InputStream is = zipFile.getInputStream(zipFile.getEntry("word/_rels/document.xml.rels"));

        DJMRelationships djmRels = null;

        JAXBContext jaxbContext;
        String target = null;
        try {
            jaxbContext = JAXBContext.newInstance(DJMRelationships.class);
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            djmRels = (DJMRelationships) jaxbUnmarshaller.unmarshal(is);

            target = djmRels.getRelationships().stream().filter(x -> x.getId().equals(id)).findFirst().get().getTarget();
        } catch (JAXBException ex) {
            LOGGER.error(ex);
        }

        return target;
    }

    /**
     * Returns a stream to an image from the DOCX-archive.
     *
     * @param docx the Docx file
     * @param name name of the desired image
     * @return the InputStream of the document.xml
     * @throws IOException
     */
    public static InputStream getImage(File docx, String name) throws IOException {
        ZipFile zipFile = new ZipFile(docx);
        // TODO: Load images dynamically (extension)
        return zipFile.getInputStream(zipFile.getEntry("word/media/" + name.toLowerCase() + ".png"));
    }

    /**
     * Loads the required font as TTF in the specified folder. If the required
     * font can't be find, a standard font will be used.
     *
     * @param fontValue name of the required font (case sensitive)
     * @param fontsFolder path of the folder with the required font(s)
     * @return FontProgram, an iText class for specifying required fonts
     */
    public static FontProgram loadFont(String fontValue, String fontsFolder) {
        FontProgram fontProgram = null;
        String fontPath = fontsFolder.concat(fontValue).concat(".ttf");
        try {
            fontProgram = FontProgramFactory.createFont(fontPath);
        } catch (IOException ex) {
            LOGGER.warn("Font {0} could not be found in {}.\n{}", fontValue, fontsFolder, ex);
            try {
                fontProgram = FontProgramFactory.createFont(StandardFontFamilies.HELVETICA);
            } catch (IOException ex2) {
                LOGGER.warn("Neither the required Font {}, nor the standard font Helvetica could be loaded.\n{}", fontValue, fontsFolder, ex);

            }
        }

        return fontProgram;
    }

    /**
     * Converts a color from String to RGB. A color can be in HEX format or as a
     * word (constant). First it will be checked, if that constant is
     * recognizable by iText. If that's not the case, it should be HEX format,
     * so it will be parsed.
     *
     * @param hex
     * @return
     * @throws Exception if an unexpected exception occurs
     */
    public static Color convertColor(String hex) {
        java.awt.Color colorAwt = null;
        Color color = null;

        /* Color "auto" will be treated as black */
        if (hex.equals("auto")) {
            color = new DeviceRgb(java.awt.Color.black);
        } else {
            /* Checks whether that color exists as constant in iText class */
            try {
                Field f = java.awt.Color.class.getField(hex);
                colorAwt = (java.awt.Color) f.get(null);
                color = new DeviceRgb(colorAwt);
            } catch (NoSuchFieldException e) {
                validateHexColor(hex);

                /* If the color constant doesn't exist, this part will be executed */
                int r = Integer.valueOf(hex.substring(0, 2), 16);
                int g = Integer.valueOf(hex.substring(2, 4), 16);
                int b = Integer.valueOf(hex.substring(4, 6), 16);
                color = new DeviceRgb(r, g, b);
            } catch (IllegalArgumentException | IllegalAccessException ex) {
                LOGGER.error(ex);
            }
        }

        return color;
    }

    /**
     * Validates a given color. Valid color formats are: #000080, #fff, #FFFFFF,
     * red etc.
     *
     * @param color the color to validate
     * @return result
     */
    private static boolean validateHexColor(String color) {
        if (color != null) {
            Pattern pattern = Pattern.compile("^#?(?:[0-9a-fA-F]{3}){1,2}$");
            Matcher matcher = pattern.matcher(color);

            if (matcher.matches()) {
                return true;
            } else {
                throw new NumberFormatException(color + " could not be recognized as a valid color");
            }
        }
        return false;
    }
}
