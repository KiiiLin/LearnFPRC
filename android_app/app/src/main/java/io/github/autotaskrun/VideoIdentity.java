package io.github.autotaskrun;

final class VideoIdentity {
    static boolean same(String a,String b){
        a=ContentRules.clean(a);b=ContentRules.clean(b);
        if(a.isEmpty()||b.isEmpty())return false;
        if(a.equals(b))return true;
        if(Math.min(a.length(),b.length())<7)return false;
        String digitsA=a.replaceAll("[^0-9]",""),digitsB=b.replaceAll("[^0-9]","");
        if(!digitsA.isEmpty()&&!digitsB.isEmpty()&&!digitsA.equals(digitsB))return false;
        // Earlier releases stored only the first title line. Match a substantial prefix on upgrade.
        String shorter=a.length()<b.length()?a:b,longer=a.length()<b.length()?b:a;
        if(shorter.length()>=10&&longer.length()<=shorter.length()*2.5&&longer.startsWith(shorter))return true;
        return ContentRules.titleMatches(a,b);
    }
    static final class Ink {
        private int neutral,dark;
        void add(int color){
            int r=(color>>16)&255,g=(color>>8)&255,b=color&255,mean=(r+g+b)/3;
            if(Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<40&&mean<225){neutral++;if(mean<145)dark++;}
        }
        boolean unread(){return neutral>=10&&dark/(double)neutral>=.35;}
    }
}
