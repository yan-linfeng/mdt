package lf.mdt;

import java.time.Duration;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

public class Translator {
    private WebDriver driver;

    private String browser = "edge";
    private String type = "deepl";

    public static Translator createTranslator(String browser, String type){
        Translator ts = new Translator();
        if(null != browser){
            ts.browser = browser;
        }
        if(null != type){
            ts.type = type;
        }
        ts.init();
        return ts;
    }

    public void init(){
        // Init Webdriver
        switch (this.browser) {
            case "edge":
                WebDriverManager.edgedriver().setup();
                driver = new EdgeDriver();
                break;
            case "safari":
                WebDriverManager.safaridriver().setup();
                driver = new SafariDriver();
                break;
            default:
                WebDriverManager.chromedriver().setup();
                driver = new ChromeDriver();
                break;
        }

        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
        // Open Translator
        switch(this.type){
            case "deepl":
                driver.get("https://www.deepl.com/translator?utm_source=lingueejp&utm_medium=linguee&utm_content=banner_translator&il=ja");
                jsExecutor.executeScript("document.querySelector('button.dl_cookieBanner--buttonClose').click()");
                driver.findElement(By.cssSelector("d-textarea[data-testid='translator-source-input']")).sendKeys("00");
                break;
            default:
                driver.get("https://translate.google.com");
                jsExecutor.executeScript("document.querySelectorAll(\"div[data-language-code='ja']\")[1].click()");
                break;
        }

    }

    public CharSequence translate(CharSequence source) {
        WebElement sourceElement;
        if("deepl".equals(type)){
            JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
            try {
                jsExecutor.executeScript("document.querySelector(\"d-textarea[data-testid='translator-source-input']\").value = '';");
                sourceElement = driver.findElement(By.cssSelector("d-textarea[data-testid='translator-source-input']"));
                sourceElement.sendKeys(source);
            } catch (Exception e) {
                jsExecutor.executeScript("document.querySelector(\"d-textarea[data-testid='translator-source-input']\").click();");
                e.printStackTrace();
            }
        }else{
            sourceElement = driver.findElement(By.cssSelector("textarea[aria-label='Source text']"));
            sourceElement.clear();
            sourceElement.sendKeys(source);
        }

        try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

        String translateTargetCssSelector;
        if("deepl".equals(type)){
            translateTargetCssSelector = "d-textarea[data-testid='translator-target-input']";
        }else{
            translateTargetCssSelector = "div[aria-live='polite']>div>div>span>span>span";
        }
        WebElement translatedElement = new WebDriverWait(driver, Duration.ofSeconds(10),Duration.ofSeconds(2))
                .until(driver -> driver.findElement(By.cssSelector(translateTargetCssSelector)));
        return translatedElement.getText();
    }

    public void close(){
        // Close Webdriver
        if(driver != null){
            driver.close();
        }
    }
}
