package com.mmu.tracker;
//import required libraries
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.HttpResponse;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class Main {
    //add form items, warnings are suppressed because they are actually needed for successful build
    @SuppressWarnings("unused")
    private JButton goButton;
    private JPanel mainPanel;
    @SuppressWarnings("unused")
    private JComboBox searchBar;

    public Main() {
        goButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                System.out.println("aww yeah press me harder");
            }
        });
    }

    //main code block
    public static void main(String[] args) {
        //load and display the form
        JFrame frame = new JFrame("COVIDASH");
        frame.setContentPane(new Main().mainPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
        //equivalent to Unirest.setTimeouts(0, 0); in older unirest-java
        Unirest.config().socketTimeout(0).connectTimeout(0);
        //use if documentation is missing
        //print("");
//        print("countries");
//        System.out.println(download("countries").getArray().getJSONObject(0));
    }
    //future function to validate user input
    static boolean isValid(){
        //convert case
        //convert spaces to hyphens
        //delete brackets
        //reject 1 letter and 3 letter input
        //remove ", Republic of China", taiwan is a free country
        return false;
    }
    //do not start apirequest with a slash!
    static JsonNode download(String apiRequest){
        //retrieve data from online api
        HttpResponse<JsonNode> response = Unirest.get("https://api.covid19api.com/" + apiRequest).asJson();
        //we must use getbody to get the part we care about
        return response.getBody();
    }
    //test function to review output. do not start apirequest with a slash!
    static void print(String apiRequest) {
        System.out.println(download(apiRequest));
    }
}
