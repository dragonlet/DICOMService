package edu.umro.DicomTest;

import java.util.*;
import java.lang.*;
import java.net.*;

public class GetOwnIP
{
    public static void main(String args[]) {
        try{
            InetAddress ownIP=InetAddress.getLocalHost();
            System.out.println("IP of my system is := "+ownIP.getHostAddress());
        }catch (Exception e){
            System.out.println("Exception caught ="+e.getMessage());
        }
    }
}