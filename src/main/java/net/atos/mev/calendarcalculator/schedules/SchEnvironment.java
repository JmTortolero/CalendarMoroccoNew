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

	public void loadFromMasterFile() {
		prop = ClassEnvLoader.loadFromMasterFile(masterFileProp);
		start = readDate(prop.getProperty("start"));
		end = readDate(prop.getProperty("end"));
		env = new Environment();
		env.loadEnvironment(prop.getProperty("env"));
		tour = Tournament.readFromLineWithAssignments(env, prop.getProperty("tournament"));
		datesFile = prop.getProperty("datesFile");
		resultsFolder = prop.getProperty("resultsFolder");
		
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
