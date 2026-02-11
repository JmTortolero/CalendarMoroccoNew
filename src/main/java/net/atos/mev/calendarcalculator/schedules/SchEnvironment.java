package net.atos.mev.calendarcalculator.schedules;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

import net.atos.mev.calendarcalculator.ClassEnvLoader;
import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Tournament;

public class SchEnvironment {
	
	LocalDate start;
	LocalDate end;
	Environment env;
	Tournament tour;
	String resultsFolder;
	String datesFile;
	Properties prop;
	
	public static String masterFileProp = "SchEnvironment.properties";

	public static SchEnvironment createFromMasterFile() {
		SchEnvironment result = new SchEnvironment();
		result.loadFromMasterFile();
		return result;
	}

	public static SchEnvironment createFromProperties(Properties properties) {
		SchEnvironment result = new SchEnvironment();
		result.loadFromProperties(properties);
		return result;
	}

	public void loadFromMasterFile() {
		Properties loadedProps = ClassEnvLoader.loadFromMasterFile(masterFileProp);
		loadFromProperties(loadedProps);
	}

	public void loadFromProperties(Properties properties) {
		prop = properties;
		start = readDate(requireProperty("start"));
		end = readDate(requireProperty("end"));
		env = new Environment();
		env.loadEnvironment(requireProperty("env"));
		tour = Tournament.readFromLineWithAssignments(env, requireProperty("tournament"));
		datesFile = prop.getProperty("datesFile");
		resultsFolder = requireProperty("resultsFolder");
	}

	private String requireProperty(String key) {
		String value = prop.getProperty(key);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Missing required property: " + key);
		}
		return value;
	}
	
	public static LocalDate readDate(String textDate) {
		return LocalDate.parse(textDate, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
	}
	
	public static String writeDate(LocalDate objDate) {
		return objDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
	}
	

	public static void main(String[] args) {
		SchEnvironment schEnv = SchEnvironment.createFromMasterFile();
		System.out.println("start=" + schEnv.start.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
		System.out.println("end=" + schEnv.end.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
		System.out.println("env.name=" + schEnv.env.name);

	}

}
