package com.harbor.capturepoc.recognize;

public class OcrItem {
    public final String text; public final int x1,y1,x2,y2; public final double score;
    public OcrItem(String t,int a,int b,int c,int d,double s){text=t;x1=a;y1=b;x2=c;y2=d;score=s;}
    public int cx(){return (x1+x2)/2;} public int cy(){return (y1+y2)/2;}
    public int width(){return x2-x1;} public int height(){return y2-y1;}
}
