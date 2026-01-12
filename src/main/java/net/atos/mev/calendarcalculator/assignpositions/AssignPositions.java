package net.atos.mev.calendarcalculator.assignpositions;

import java.net.URL;
import java.security.SecureRandom;
import java.util.*;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Team;
import net.atos.mev.calendarcalculator.TeamsAssignments;
import net.atos.mev.calendarcalculator.Tournament;
import net.atos.mev.calendarcalculator.checks.Check;
import net.atos.mev.calendarcalculator.checks.CheckProcessor;
import net.atos.mev.calendarcalculator.checks.ForcePositionsCheck;
import net.atos.mev.calendarcalculator.fileio.AssignmentsFileManager;
import net.atos.mev.calendarcalculator.fileio.PositionsFileManager;

public class AssignPositions {

	int foundTeams;
	AssignmentsFileManager fileManager;
	boolean generateRandom = false;
	boolean generateOnlyOne = false;
	int[] chooseAttemptedPos;
	Environment env;
	ArrayList<AssignPositions> dependentExecutions = new ArrayList<AssignPositions>();

	public boolean assignPositions(Tournament tour) {
		ArrayList<Team> assignedTeams = new ArrayList<Team>();
		tour.mapPosToTeams = new TeamsAssignments();
		if(fileManager == null) {
			fileManager = new AssignmentsFileManager();
			fileManager.prepareOutputTournament(tour);
		}
		chooseAttemptedPos = new int[tour.env.nTeams];
		for(int nPos=0; nPos < tour.env.nTeams; nPos++) {
			chooseAttemptedPos[nPos] = nPos+1;
		}
		if(generateRandom) {
			SecureRandom random = new SecureRandom();
			for (int i=0; i < chooseAttemptedPos.length; i++) {
				int randomIndexToSwap = random.nextInt(chooseAttemptedPos.length);
				int temp = chooseAttemptedPos[randomIndexToSwap];
				chooseAttemptedPos[randomIndexToSwap] = chooseAttemptedPos[i];
				chooseAttemptedPos[i] = temp;
			}
		}
		foundTeams = 0;
		boolean foundResult = recursiveAttemptAssignPos(tour, assignedTeams);
		if(foundResult) {
			tour.applyMapPosToTeams();
			System.out.println("++++++++++++++");
		} else {
			/////System.out.println("Not found result");
		}
		return foundResult;
	}

	Set<Tournament> generated = new HashSet<>();

	public boolean recursiveAttemptAssignPos(Tournament tour, ArrayList<Team> assignedTeams) {
		boolean foundResult = false;
		if(assignedTeams.size() == tour.env.teams.length) {
			if (generated.contains(tour)) {
				System.out.println("REPEATED "+ foundTeams + " - " + tour.mapPosToTeams);
				return false;
			}
			generated.add(tour);
			foundTeams++;
			System.out.println(foundTeams + " - " + tour.mapPosToTeams);
			fileManager.writeTournamentPos(tour);
			return true;
		} else {
			Team nextTeamToAssign = tour.env.teams[assignedTeams.size()];
			for(int chooseAttemptedPosIdx=0; chooseAttemptedPosIdx < chooseAttemptedPos.length; chooseAttemptedPosIdx++) {
				int attemptedPos = chooseAttemptedPos[chooseAttemptedPosIdx];
				if(tour.mapPosToTeams.get(attemptedPos) == null) {
					tour.mapPosToTeams.put(attemptedPos, nextTeamToAssign);
					if(CheckProcessor.runAllChecksForTeam(tour, tour.env.checks, assignedTeams, nextTeamToAssign)) {
						assignedTeams.add(nextTeamToAssign);
						foundResult = recursiveAttemptAssignPos(tour, assignedTeams);
						if(foundResult && generateOnlyOne) {
							return true;
						}
						assignedTeams.remove(nextTeamToAssign);
					}
					tour.mapPosToTeams.put(attemptedPos, null);
				}
			}
		}
		return false;
	}

	public boolean isGenerateRandom() {
		return generateRandom;
	}

	public void setGenerateRandom(boolean generateRandom) {
		this.generateRandom = generateRandom;
	}

	public boolean isGenerateOnlyOne() {
		return generateOnlyOne;
	}

	public void setGenerateOnlyOne(boolean generateOnlyOne) {
		this.generateOnlyOne = generateOnlyOne;
	}

	public static void main(String[] args) {
		try {
			Properties masterAssignProp = new Properties();
			URL murl = ClassLoader.getSystemResource("AssignPositions.properties");
			masterAssignProp.load(murl.openStream());
			Properties assignProp = new Properties();
			URL url = ClassLoader.getSystemResource(masterAssignProp.getProperty("assign.file"));
			assignProp.load(url.openStream());

			int resultsToGenerate = Integer.valueOf(assignProp.get("nResults").toString());
			ArrayList<AssignPositions> allEnv = new ArrayList<AssignPositions>();
			for(int i=1; i<100; i++) {
				String envConfig = assignProp.getProperty("env."+i);
				if(envConfig != null) {
					AssignPositions newExec = new AssignPositions();
					Environment newEnv = new Environment();
					newEnv.loadEnvironment(envConfig);
					newExec.env=newEnv;
					newExec.setGenerateRandom(true);
					newExec.setGenerateOnlyOne(true);
					allEnv.add(newExec);
					for(int j=1; j<100; j++) {
						String dependentEnvConfig = assignProp.getProperty("env."+i+"."+j);
						if(dependentEnvConfig != null) {
							AssignPositions newDependentExec = new AssignPositions();
							Environment newEnvDependent = new Environment();
							newEnvDependent.loadEnvironment(dependentEnvConfig);
							newDependentExec.env=newEnvDependent;
							newDependentExec.setGenerateRandom(true);
							newDependentExec.setGenerateOnlyOne(true);
							newExec.dependentExecutions.add(newDependentExec);
						}
					}
				}
			}

			ArrayList<String> posFolders = new ArrayList<String>();
			ArrayList<String> stadiumSharing = new ArrayList<String>();
			for(int k=1; k<100; k++) {
				String posFolderK = assignProp.getProperty("posfolder."+k);
				if(posFolderK != null) {
					posFolders.add(posFolderK);
				}
				String stadiumShareK = assignProp.getProperty("teams.NotAtHomeTogether."+k);
				if(stadiumShareK != null) {
					stadiumSharing.add(stadiumShareK);
				}
			}
			String[][] stadiumSharingTeams = new String[stadiumSharing.size()][];
			for(int l=0; l<stadiumSharing.size(); l++) {
				String[] teamsInShare = stadiumSharing.get(l).split(",");
				stadiumSharingTeams[l] = teamsInShare;
			}


			SecureRandom rand = new SecureRandom();
			int foundResults = 0;
			for(int i=0; i<100000 && foundResults < resultsToGenerate; i++) {
				String[] posFolder = posFolders.get(i%posFolders.size()).split(",");
				int nPositionsCombination = rand.nextInt(Integer.valueOf(posFolder[1]));
				/////System.out.println(nPositionsCombination);
				for(AssignPositions execution : allEnv) {
					Tournament t1 = PositionsFileManager.readTournamentPos(execution.env, nPositionsCombination, posFolder[0]);
					if(execution.assignPositions(t1)) {
						foundResults++;
						for(AssignPositions dependentExecution : execution.dependentExecutions) {
							Tournament depTour = PositionsFileManager.readTournamentPos(dependentExecution.env, rand.nextInt(Integer.valueOf(posFolder[1])), posFolder[0]);
							linkStadiumSharingTeams(t1, depTour, stadiumSharingTeams);
							if(!dependentExecution.assignPositions(depTour)) {
								System.out.println("======== FAILED TO DO RELATED CALENDAR ========");
							}
						}
					}
				}
			}

			AssignmentStatistics stats = new AssignmentStatistics();
			for(AssignPositions execution : allEnv) {
				stats.calculateStatistics(execution.env, execution.fileManager.getLastFolderName(), foundResults);
				for(AssignPositions dependentExecution : execution.dependentExecutions) {
					stats.calculateStatistics(dependentExecution.env, dependentExecution.fileManager.getLastFolderName(), foundResults);
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	static void linkStadiumSharingTeams(Tournament t1, Tournament t2, String[][] stadiumSharingTeams) {
		for(String[] sharingCouple : stadiumSharingTeams) {
			int posOfTeamInT1 = t1.mapPosToTeams.getKeyOfTeam(t1.env.searchTeamByCode(sharingCouple[0]));
			int posOfTeamInT2;
			if(posOfTeamInT1%2==0) {
				posOfTeamInT2=posOfTeamInT1-1;
			} else {
				posOfTeamInT2=posOfTeamInT1+1;
			}
			for(Check genericCheck : t2.env.checks) {
				if(genericCheck instanceof ForcePositionsCheck) {
					ForcePositionsCheck specificCheck = (ForcePositionsCheck)genericCheck;
					if(specificCheck.getTeam1().getCode().equals(sharingCouple[1])){
						specificCheck.setRound(posOfTeamInT2);
					}
				}
			}
		}
	}

}
