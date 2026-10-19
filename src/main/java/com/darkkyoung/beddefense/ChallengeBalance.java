package com.darkkyoung.beddefense;

/** Provisional balance, deliberately independent from saved lifecycle and difficulty labels. */
public final class ChallengeBalance {
    private ChallengeBalance(){}
    public static final int SUNRISE_HP_PER_SURVIVOR=1;
    public static final int DIMENSION_DEATH_HP=20;
    public static final int REPAIR_HP_PER_IRON=10;
    public static final int ACTIVE_RAIDERS_PER_DIMENSION=128;
    public static final int SPAWNS_PER_SECOND=8;
    public static final int PATH_REQUESTS_PER_SECOND=32;
    public static int rosterDay(long day,int multiplier){return (int)Math.min(100,1+Math.max(0,day-1)*Math.max(1,multiplier));}
    public static boolean preferBed(double playerDistanceSquared,double bedDistanceSquared){return bedDistanceSquared<playerDistanceSquared;}
}
