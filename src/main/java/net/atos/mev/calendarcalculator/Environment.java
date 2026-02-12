package net.atos.mev.calendarcalculator;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Properties;

import net.atos.mev.calendarcalculator.checks.Check;
import net.atos.mev.calendarcalculator.checks.CheckProcessor;

public class Environment {

	public String name;
	public String code;
	public int nTeams = 0;
	public int nRounds = 0;
	public int matchesPerRound = 0;
	public Team[] teams = new Team[0];
	public int nChecks = 0;
	public Check[] checks;
	Properties originalProp;

	public void loadEnvironment(String propertiesFile) {
		try (InputStream inputStream = resolvePropertiesInputStream(propertiesFile)) {
			originalProp = new java.util.Properties();
			originalProp.load(inputStream);
			name = originalProp.getProperty("name");
			code = originalProp.getProperty("code");
			nTeams = Integer.parseInt(originalProp.getProperty("nTeams"));
			matchesPerRound = nTeams/2;
			nRounds = Integer.parseInt(originalProp.getProperty("nRounds"));
			teams = new Team[nTeams];
			for(int i=0; i<nTeams; i++) {
				Team newTeam = new Team();
				newTeam.setCode(originalProp.getProperty("team."+i));
				newTeam.setName(originalProp.getProperty("team."+newTeam.getCode()));
				newTeam.setEnv(this);
				teams[i] = newTeam;
			}
			nChecks = Integer.parseInt(originalProp.getProperty("nChecks"));
			ArrayList<Check> newChecks = new ArrayList<Check>();
			for(int iCheck=1; iCheck <= nChecks; iCheck++) {
				String newCheckString = originalProp.getProperty("check."+iCheck);
				if(newCheckString != null) {
					Check newCheckObject = CheckProcessor.createCheck(this, newCheckString);
					if(newCheckObject != null) {
						newChecks.add(newCheckObject);
					}
				}
			}
			checks = new Check[newChecks.size()];
			checks = newChecks.toArray(checks);
			
		} catch (Exception e) {
			throw new IllegalStateException("Cannot load environment properties: " + propertiesFile, e);
		}
	}

	private InputStream resolvePropertiesInputStream(String propertiesFile) throws IOException {
		Path configured = Path.of(propertiesFile);
		if (configured.isAbsolute() && Files.exists(configured)) {
			return Files.newInputStream(configured);
		}

		Path[] candidates = new Path[] {
			Path.of(propertiesFile),
			Path.of("./data/dataFiles").resolve(propertiesFile),
			Path.of("./data").resolve(propertiesFile),
			Path.of("/config/competitions").resolve(propertiesFile)
		};

		for (Path candidate : candidates) {
			Path normalized = candidate.toAbsolutePath().normalize();
			if (Files.exists(normalized) && Files.isRegularFile(normalized)) {
				return Files.newInputStream(normalized);
			}
		}

		URL url = ClassLoader.getSystemResource(propertiesFile);
		if (url != null) {
			return url.openStream();
		}

		throw new IllegalStateException("Environment properties not found: " + propertiesFile);
	}
	
	public Team searchTeamByCode(String code) {
		for(Team t : teams) {
			if(t.getCode().equals(code)) {
				return t;
			}
		}
		return null;
	}

	public static void main(String[] args) {
		Environment env = new Environment();
		env.loadEnvironment("Test6Teams.properties");
		for(Team t: env.teams) {
			System.out.println(t.toString());
		}
	}

	public Properties getOriginalProp() {
		return originalProp;
	}

	public void setOriginalProp(Properties originalProp) {
		this.originalProp = originalProp;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

}
