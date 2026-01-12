package net.atos.mev.calendarcalculator;

import java.net.URL;
import java.util.Properties;

public class ClassEnvLoader {

	public static Properties loadFromMasterFile(String masterFile) {
		try {
			Properties prop = new Properties();
			URL url = ClassLoader.getSystemResource(masterFile);
			prop.load(url.openStream());
			String configFile = prop.getProperty("configFile");
			return loadFromConfigFile(configFile);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static Properties loadFromConfigFile(String configFile) {
		try {
			Properties prop = new Properties();
			URL url = ClassLoader.getSystemResource(configFile);
			prop.load(url.openStream());
			return prop;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}


	public static void main(String[] args) {

	}

}
