package net.atos.mev.calendarcalculator.checks;

import java.util.ArrayList;
import java.util.Arrays;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Team;
import net.atos.mev.calendarcalculator.Tournament;

public class CheckProcessor {

	public static boolean runAllChecksForTeam(Tournament tour, Check[] checks, ArrayList<Team> assignedTeams, Team nextTeamToAssign) {
//		System.out.println(tour.mapPosToTeams + " - " + nextTeamToAssign.getCode());
		for(Check currentCheckGeneric : checks) {
			if(currentCheckGeneric instanceof DerbyTeamsCheck) {
				DerbyTeamsCheck currentCheck = (DerbyTeamsCheck)currentCheckGeneric;
				if(currentCheck.getTeam1().equals(nextTeamToAssign) && assignedTeams.contains(currentCheck.getTeam2()) || currentCheck.getTeam2().equals(nextTeamToAssign) && assignedTeams.contains(currentCheck.getTeam1())) {
//					System.out.println("------------- RUNNING CHECK DerbyTeamsCheck -------------");
					boolean passesCheck = currentCheck.check(tour);
					if(!passesCheck) {
						return false;
					}
				}
			}
			if(currentCheckGeneric instanceof LimitHomeTogetherCheck) {
				LimitHomeTogetherCheck currentCheck = (LimitHomeTogetherCheck)currentCheckGeneric;
				if(currentCheck.getTeam1().equals(nextTeamToAssign) && assignedTeams.contains(currentCheck.getTeam2()) || currentCheck.getTeam2().equals(nextTeamToAssign) && assignedTeams.contains(currentCheck.getTeam1())) {
//					System.out.println("------------- RUNNING CHECK LimitHomeTogetherCheck -------------");
					boolean passesCheck = currentCheck.check(tour);
					if(!passesCheck) {
						return false;
					}
				}
			}
			else if(currentCheckGeneric instanceof PlayTogetherTeamsCheck) {
				PlayTogetherTeamsCheck currentCheck = (PlayTogetherTeamsCheck)currentCheckGeneric;
				if(currentCheck.getTeam1().equals(nextTeamToAssign) && assignedTeams.contains(currentCheck.getTeam2()) || currentCheck.getTeam2().equals(nextTeamToAssign) && assignedTeams.contains(currentCheck.getTeam1())) {
//					System.out.println("------------- RUNNING CHECK PlayTogetherTeamsCheck -------------");
					boolean passesCheck = currentCheck.check(tour);
					if(!passesCheck) {
						return false;
					}
				}
			}
			else if(currentCheckGeneric instanceof MatchNotInRoundCheck) {
				MatchNotInRoundCheck currentCheck = (MatchNotInRoundCheck)currentCheckGeneric;
				if(currentCheck.getTeam1().equals(nextTeamToAssign) && assignedTeams.contains(currentCheck.getTeam2()) || currentCheck.getTeam2().equals(nextTeamToAssign) && assignedTeams.contains(currentCheck.getTeam1())) {
//					System.out.println("------------- RUNNING CHECK MatchNotInRoundCheck -------------");
					boolean passesCheck = currentCheck.check(tour);
					if(!passesCheck) {
						return false;
					}
				}
			}
			else if(currentCheckGeneric instanceof NotConsecutiveFarAwayCheck) {
				NotConsecutiveFarAwayCheck currentCheck = (NotConsecutiveFarAwayCheck)currentCheckGeneric;
				boolean newTeamInCheck = false;
				boolean allTeamsHavePos = true;
				for(Team t : currentCheck.farTeams) {
					if(t.equals(nextTeamToAssign)) {
						newTeamInCheck = true;
					} else if(!assignedTeams.contains(t)) {
						allTeamsHavePos = false;
					}
				}
				if(newTeamInCheck && allTeamsHavePos) {
//					System.out.println("------------- RUNNING CHECK NotConsecutiveFarAwayCheck -------------");
					boolean passesCheck = currentCheck.check(tour);
					if(!passesCheck) {
						return false;
					}
				}
			}
			else if(currentCheckGeneric instanceof LimitAgainstTeamsInRoundsCheck) {
				LimitAgainstTeamsInRoundsCheck currentCheck = (LimitAgainstTeamsInRoundsCheck)currentCheckGeneric;
				if(Arrays.asList(currentCheck.getSeparateTeams()).contains(nextTeamToAssign)) {
//					System.out.println("------------- RUNNING CHECK LimitAgainstTeamsInRoundsCheck -------------");
					boolean passesCheck = currentCheck.check(tour);
					if(!passesCheck) {
						return false;
					}
				}
			}
			else if(currentCheckGeneric instanceof LimitAgainstTeamsInRoundsHomeCheck) {
				LimitAgainstTeamsInRoundsHomeCheck currentCheck = (LimitAgainstTeamsInRoundsHomeCheck)currentCheckGeneric;
				if(Arrays.asList(currentCheck.getSeparateTeams()).contains(nextTeamToAssign)) {
//					System.out.println("------------- RUNNING CHECK LimitAgainstTeamsInRoundsCheck -------------");
					boolean passesCheck = currentCheck.check(tour);
					if(!passesCheck) {
						return false;
					}
				}
			}
			else if(currentCheckGeneric instanceof SeparateMatchesCheck) {
				SeparateMatchesCheck currentCheck = (SeparateMatchesCheck)currentCheckGeneric;
				if(Arrays.asList(currentCheck.getSeparateTeams()).contains(nextTeamToAssign)) {
//					System.out.println("------------- RUNNING CHECK SeparateMatchesCheck -------------");
					boolean passesCheck = currentCheck.check(tour);
					if(!passesCheck) {
						return false;
					}
				}
			}
			else if(currentCheckGeneric instanceof ForcePositionsCheck) {
				ForcePositionsCheck currentCheck = (ForcePositionsCheck)currentCheckGeneric;
				if(currentCheck.getTeam1().equals(nextTeamToAssign)) {
//					System.out.println("------------- RUNNING CHECK ForcePositionsCheck -------------");
					boolean passesCheck = currentCheck.check(tour);
					if(!passesCheck) {
						return false;
					}
				}
			}
		}
		return true;
	}

	
	/**
	 * @param checkDescription: For example:
	 * 	check.1=PlayTogetherTeamsCheck,RSB,MCO
		check.3=DerbyTeamsCheck,RSB,HAG
		check.6=MatchNotInRoundCheck,RCA,WAC
	 * @return
	 */
	public static Check createCheck(Environment env, String checkDescription) {
		Check result = null;
		String[] parts = checkDescription.split(",");
		if(parts[0] != null) {
			switch(parts[0]) {
			case "DerbyTeamsCheck":
				if(!checkLength(checkDescription, parts, "DerbyTeamsCheck", 3)) return null;
				Team team1d = lookupTeam(env, checkDescription, parts[1]);
				Team team2d = lookupTeam(env, checkDescription, parts[2]);
				if(team1d == null || team2d == null) {
					return null;
				}
				result = new DerbyTeamsCheck(team1d, team2d);
				break;
			case "LimitHomeTogetherCheck":
				if(!checkLength(checkDescription, parts, "LimitHomeTogetherCheck", 4)) return null;
				Team team1ld = lookupTeam(env, checkDescription, parts[1]);
				Team team2ld = lookupTeam(env, checkDescription, parts[2]);
				int limitHT = Integer.valueOf(parts[3]);
				if(team1ld == null || team2ld == null) {
					return null;
				}
				result = new LimitHomeTogetherCheck(team1ld, team2ld, limitHT);
				break;
			case "PlayTogetherTeamsCheck":
				if(!checkLength(checkDescription, parts, "PlayTogetherTeamsCheck", 3)) return null;
				Team team1p = lookupTeam(env, checkDescription, parts[1]);
				Team team2p = lookupTeam(env, checkDescription, parts[2]);
				if(team1p == null || team2p == null) {
					return null;
				}
				result = new PlayTogetherTeamsCheck(team1p, team2p);
				break;
			case "MatchNotInRoundCheck":
				Team team1m = lookupTeam(env, checkDescription, parts[1]);
				Team team2m = lookupTeam(env, checkDescription, parts[2]);
				if(team1m == null || team2m == null) {
					return null;
				}
				int[] forbiddenRounds = new int[parts.length-3];
				for(int fr=3; fr<parts.length; fr++) {
					int roundm = Integer.valueOf(parts[fr])-1;
					if(roundm > env.nRounds) {
						roundm = roundm - env.nRounds;
					}
					forbiddenRounds[fr-3] = roundm;
				}
				result = new MatchNotInRoundCheck(team1m, team2m, forbiddenRounds);
				break;
			case "NotConsecutiveFarAwayCheck":
				Team[] farTeams = new Team[parts.length-1];
				for(int nFarTeam=0; nFarTeam<farTeams.length; nFarTeam++) {
					Team newFarTeam = lookupTeam(env, checkDescription, parts[nFarTeam+1]);
					if(newFarTeam == null) {
						return null;
					}
					farTeams[nFarTeam] = newFarTeam;
				}
				result = new NotConsecutiveFarAwayCheck(farTeams);
				break;
			//LimitAgainstTeamsInRoundsCheck,3,3,RCA,WAC,RSB,3,4,8,10,11,13,14,19,20,21,24
			case "LimitAgainstTeamsInRoundsCheck": {
				int limit = Integer.valueOf(parts[1]);
				int amountOfTeams = Integer.valueOf(parts[2]);
				Team[] latrTeams = new Team[amountOfTeams];
				for (int nLatrTeam = 0; nLatrTeam < latrTeams.length; nLatrTeam++) {
					Team newLatrTeam = lookupTeam(env, checkDescription, parts[nLatrTeam + 3]);
					if (newLatrTeam == null) {
						return null;
					}
					latrTeams[nLatrTeam] = newLatrTeam;
				}
				int[] latrRounds = new int[parts.length - 3 - amountOfTeams];
				for (int nLatrRound = 0; nLatrRound < latrRounds.length; nLatrRound++) {
					latrRounds[nLatrRound] = Integer.valueOf(parts[3 + amountOfTeams + nLatrRound]) - 1;
				}
				result = new LimitAgainstTeamsInRoundsCheck(limit, latrTeams, latrRounds);
				break;
			}

				case "LimitAgainstTeamsInRoundsHomeCheck": {
					int limit = Integer.valueOf(parts[1]);
					int amountOfTeams = Integer.valueOf(parts[2]);
					Team[] latrTeams = new Team[amountOfTeams];
					for (int nLatrTeam = 0; nLatrTeam < latrTeams.length; nLatrTeam++) {
						Team newLatrTeam = lookupTeam(env, checkDescription, parts[nLatrTeam + 3]);
						if (newLatrTeam == null) {
							return null;
						}
						latrTeams[nLatrTeam] = newLatrTeam;
					}
					int[] latrRounds = new int[parts.length - 3 - amountOfTeams];
					for (int nLatrRound = 0; nLatrRound < latrRounds.length; nLatrRound++) {
						latrRounds[nLatrRound] = Integer.valueOf(parts[3 + amountOfTeams + nLatrRound]) - 1;
					}
					result = new LimitAgainstTeamsInRoundsHomeCheck(limit, latrTeams, latrRounds);
					break;
				}

			case "SeparateMatchesCheck":
				Team[] separateTeams = new Team[parts.length-2];
				int separation=Integer.valueOf(parts[1]);
				for(int nSeparateTeam=0; nSeparateTeam<separateTeams.length; nSeparateTeam++) {
					Team newSeparateTeam = lookupTeam(env, checkDescription, parts[nSeparateTeam+2]);
					if(newSeparateTeam == null) {
						return null;
					}
					separateTeams[nSeparateTeam] = newSeparateTeam;
				}
				result = new SeparateMatchesCheck(separateTeams, separation);
				break;
			case "ForcePositionsCheck":
				if(!checkLength(checkDescription, parts, "ForcePositionsCheck", 3)) return null;
				Team team1fp = lookupTeam(env, checkDescription, parts[1]);
				if(team1fp == null || team1fp == null) {
					return null;
				}
				int forcedPosition = Integer.valueOf(parts[2]);
				result = new ForcePositionsCheck(team1fp, forcedPosition);
				break;
			}
		}
		return result;
	}
	
	private static boolean checkLength(String checkDescription, String[] parts, String checkName, int nParameters) {
		if(parts.length != nParameters) {
			System.out.println("ERROR: " +checkName+ " should have "+nParameters+" parameters in: "+ checkDescription);
			return false;
		}
		return true;
	}
	
	private static Team lookupTeam(Environment env, String checkDescription, String teamCode) {
		Team result = env.searchTeamByCode(teamCode);
		if(result == null) {
			System.out.println("ERROR: Team not existing team " + teamCode + " in: "+ checkDescription);
		}
		return result;
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
