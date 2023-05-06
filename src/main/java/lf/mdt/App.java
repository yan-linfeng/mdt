package lf.mdt;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.google.common.base.Charsets;
import com.vladsch.flexmark.formatter.Formatter;
import com.vladsch.flexmark.formatter.RenderPurpose;
import com.vladsch.flexmark.formatter.TranslationHandler;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;

import io.github.bonigarcia.wdm.WebDriverManager;

public class App {
    private static WebDriver driver;

    private static String sourceLanguage = "en";
    private static String targetLanguage = "ja";
    private static String mdFile;
    private static String mdFolder;

    public static void main(String[] args) throws IOException {
        // Parse command line arguments
        parseCommandLine(args);

        // Translate single markdown file
        if(null != mdFile){
            if(!Files.exists(Paths.get(mdFile), LinkOption.NOFOLLOW_LINKS)){
                System.out.println("mdfile not valid");
                System.exit(0);
            }else{
                initializeWebDriver();
                String translatedMD = translateMDFile(mdFile);
                String translatedFileName = mdFile.substring(mdFile.lastIndexOf("/")+1).replaceFirst("\\.", "_"+targetLanguage+".");
                Files.write(Paths.get(translatedFileName), translatedMD.getBytes(Charsets.UTF_8), StandardOpenOption.CREATE_NEW);
                driver.close();
            }
        }

        // Translate markdown folder
        if(null != mdFolder){
            if(!Files.exists(Paths.get(mdFolder), LinkOption.NOFOLLOW_LINKS)){
                System.out.println("mdfolder not valid");
                System.exit(0);
            }else{
                String srcFolder = mdFolder;
                if(!mdFolder.endsWith("/")){
                    srcFolder += "/";
                }
                String[] srcDirs = srcFolder.split("/");
                String destFolder =  srcDirs[srcDirs.length-1]+"_jp/";

                Paths.get(destFolder).toFile().mkdir();

                final Path source = Paths.get(srcFolder);
                final Path target = Paths.get(destFolder);
                initializeWebDriver();
                Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                            throws IOException
                        {
                            Path targetdir = target.resolve(source.relativize(dir));
                            targetdir.toFile().mkdir();
                            return FileVisitResult.CONTINUE;
                        }
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException
                        {
                            if(file.toString().endsWith("md") || file.toString().endsWith("MD")){
                                System.out.println(target.resolve(source.relativize(file)));
                                Path translatedMDPath = target.resolve(source.relativize(file));
    
                                String translated = translateMDFile(file.toFile().getAbsolutePath());
                                Files.write(translatedMDPath, translated.getBytes(Charsets.UTF_8), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                            }else{
                                Files.copy(file, target.resolve(source.relativize(file)));
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                driver.close();
            }
        }

    }

    private static void parseCommandLine(String[] args) {
        Options options = new Options();
        options.addOption("srclang", "source-language", true, "set source language");
        options.addOption("tlang", "target-language", true, "set target language");
        options.addOption("mdfile", "md-file", true, "markdown file to be translated");
        options.addOption("mdfolder", "md-folder", true, "markdown file folder to be translated");

        CommandLineParser parser = new DefaultParser();
        HelpFormatter helper = new HelpFormatter();
        try {
            CommandLine cmd = parser.parse(options, args);
            if(cmd.hasOption("srclang")){
                sourceLanguage = cmd.getOptionValue("srclang");
            }
            if(cmd.hasOption("tlang")){
                targetLanguage = cmd.getOptionValue("tlang");
            }
            if(cmd.hasOption("mdfile")){
                mdFile = cmd.getOptionValue("mdfile");
            }
            if(cmd.hasOption("mdfolder")){
                mdFolder = cmd.getOptionValue("mdfolder");
            }
            if(!cmd.hasOption("mdfile") && !cmd.hasOption("mdfolder")){
                System.out.println("No mdfile or mdfolder !");
                helper.printHelp("Usage:", options);
                System.exit(0);
            }
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            helper.printHelp("Usage:", options);
            System.exit(0);
        }
    }


    private static void initializeWebDriver() {
        WebDriverManager.edgedriver().setup();
        driver = new EdgeDriver();
        driver.get("https://translate.google.com");

        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
        jsExecutor.executeScript("document.querySelectorAll(\"div[data-language-code='"+targetLanguage+"']\")[1].click()");
        jsExecutor.executeScript("document.querySelectorAll(\"div[data-language-code='"+sourceLanguage+"']\")[0].click()");
    }


    private static String translateMDFile(String filePath) {
        MutableDataSet OPTIONS = new MutableDataSet()
                .set(Parser.BLANK_LINES_IN_AST, true)
                .set(Parser.HTML_FOR_TRANSLATOR, true)
                .set(Parser.PARSE_INNER_HTML_COMMENTS, true)
                .set(Formatter.MAX_TRAILING_BLANK_LINES, 0);

        Parser PARSER = Parser.builder(OPTIONS).build();
        Formatter FORMATTER = Formatter.builder(OPTIONS).build();

        String markdown = "";
        try {
            markdown = new String(Files.readAllBytes(Paths.get(filePath)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // 1. Parse the document to get markdown AST
        Document node = PARSER.parse(markdown);

        // 2. Format the document to get markdown strings for translation
        TranslationHandler handler = FORMATTER.getTranslationHandler();
        String formattedOutput = FORMATTER.translationRender(node, handler,
                RenderPurpose.TRANSLATION_SPANS);

        // 3. Get the strings to be translated from translation handler
        List<String> translatingTexts = handler.getTranslatingTexts();

        // 4. Have the strings translated by your translation service of preference
        ArrayList<CharSequence> translatedTexts = new ArrayList<>(translatingTexts.size());
        for (CharSequence text : translatingTexts) {
            CharSequence translated = translate(text);
            translatedTexts.add(translated);
        }

        // 5. Set the translated strings in the translation handler
        handler.setTranslatedTexts(translatedTexts);

        // 6. Generate markdown with placeholders for non-translating string and out of context translations
        // the rest will already contain translated text
        String partial = FORMATTER.translationRender(node, handler,
                RenderPurpose.TRANSLATED_SPANS);

        // 7. Parse the document with placeholders
        Node partialDoc = PARSER.parse(partial);

        // 8. Generate the final translated markdown
        String translated = FORMATTER.translationRender(partialDoc, handler,
                RenderPurpose.TRANSLATED);

        return translated;
    }

    private static CharSequence translate(CharSequence source) {
        WebElement sourceElement = driver.findElement(By.cssSelector("textarea[aria-label='Source text']"));
        sourceElement.clear();
        sourceElement.sendKeys(source);

        try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

        WebElement translatedElement = new WebDriverWait(driver, Duration.ofSeconds(10),Duration.ofSeconds(2))
                .until(driver -> driver.findElement(By.cssSelector("div[aria-live='polite']>div>div>span>span>span")));
        return translatedElement.getText();
    }

}
