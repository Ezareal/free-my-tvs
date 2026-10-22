package io.freemytv;

import java.util.Random;

public class EducationRulesTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(EducationRules.remaining(0,0)==10800000, "three hour allowance");
        check(EducationRules.remaining(10799999,0)==1, "last millisecond available");
        check(EducationRules.remaining(10800000,0)==0, "checkpoint at exactly three hours");
        check(EducationRules.remaining(10800000,1)==1800000, "one reward is 30 minutes");
        check(EducationRules.remaining(12600000,1)==0, "checkpoint returns after reward");
        check(EducationRules.remaining(0, -1)==10800000, "negative rewards ignored");
        Random r=new Random(831);
        boolean subtraction=false, multiplication=false;
        for(int day=0;day<100;day++) for(int i=0;i<100;i++) {
            EducationRules.Problem p=EducationRules.problem(r,day);
            check(p.answer>=0 && p.answer<=50, "bounded non-negative answer for keypad");
            if(day==0) check(p.operator.equals("+")&&p.answer<=10,"start with additions up to ten");
            if(p.operator.equals("+"))check(p.answer==p.a+p.b,"correct addition");
            if(p.operator.equals("−")){subtraction=true;check(day>=3&&p.answer==p.a-p.b,"gradual subtraction");}
            if(p.operator.equals("×")){multiplication=true;check(day>=14&&p.answer==p.a*p.b,"later multiplication");}
        }
        check(subtraction && multiplication,"all later operations reachable");
        System.out.println("PASS: budget boundaries, rewards, and 10,000 progressively generated questions");
    }
}
