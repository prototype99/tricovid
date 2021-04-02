package com.mmu.tracker;
//import required libraries
import kong.unirest.Unirest;
import javax.swing.*;
import org.apache.http.HttpResponse;

public class Main {
    //add form items, warnings are suppressed because they are actually needed for successful build
    @SuppressWarnings("unused")
    private JButton goButton;
    private JPanel mainPanel;
    @SuppressWarnings("unused")
    private JTextField searchBar;
    //main code block
    public static void main(String[] args) {
        //load and display the form
        JFrame frame = new JFrame("COVIDASH");
        frame.setContentPane(new Main().mainPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
        //equivalent to Unirest.setTimeouts(0, 0);
        Unirest.config().socketTimeout(0)
                .connectTimeout(0);
        HttpResponse response = (HttpResponse) Unirest.get("https://api.covid19api.com/")
                .asString();
        System.out.println(response);
    }
}
