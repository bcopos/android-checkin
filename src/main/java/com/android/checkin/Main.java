package com.android.checkin;
import com.android.checkin.Checkin;

public class Main {
    private static void usage() {
        System.out.println("Usage: java -jar android-checkin.jar <email> <password>");
    }

    public static void main(String[] args) {
        try {
            if(args.length < 2) {
                usage();
                return;
            }
            String email = args[0];
            String password = args[1];

            String gsfId = new Checkin(email, password).checkin();
            System.out.println("GSF_ID: " + gsfId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
