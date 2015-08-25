package com.codename1.demos.flickrconcentration;


import com.codename1.components.InfiniteProgress;
import com.codename1.components.SpanLabel;
import com.codename1.io.ConnectionRequest;
import com.codename1.io.JSONParser;
import com.codename1.io.NetworkManager;
import com.codename1.ui.Button;
import com.codename1.ui.Component;
import com.codename1.ui.Container;
import com.codename1.ui.Dialog;
import com.codename1.ui.Display;
import com.codename1.ui.EncodedImage;
import com.codename1.ui.Form;
import com.codename1.ui.Image;
import com.codename1.ui.Label;
import com.codename1.ui.TextField;
import com.codename1.ui.URLImage;
import com.codename1.ui.animations.FlipTransition;
import com.codename1.ui.layouts.BorderLayout;
import com.codename1.ui.layouts.BoxLayout;
import com.codename1.ui.layouts.GridLayout;
import com.codename1.ui.plaf.Border;
import com.codename1.ui.plaf.UIManager;
import com.codename1.ui.util.Resources;
import com.codename1.ui.util.UITimer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;


public class ClassicFlickrConcentration {

    private Form current;
    private Resources theme;
    
    // Stores the image for the back of a card
    private EncodedImage cardBack;
    
    // number of rows in the card grid
    private int rows = 4;
    
    // number of columns in the card grid
    private int cols = 4;
    
    // Game allows you to flip one card, then a second one to see if it matces
    // the first.  This is a placeholder for the url of the image in the "first"
    // card that was flipped.
    private String currentUrl;
    
    // Placeholder for the button containing the "first" image that was flipped
    private Button currentButton;

    public void init(Object context) {
        try {
            theme = Resources.openLayered("/theme");
            UIManager.getInstance().setThemeProps(theme.getTheme(theme.getThemeResourceNames()[0]));
        } catch(IOException e){
            e.printStackTrace();
        }
        cardBack = (EncodedImage)theme.getImage("card-back-1024x1024.png").scaled(Display.getInstance().getDisplayWidth()/cols, Display.getInstance().getDisplayHeight()/rows);
        
    }
    
    public void start() {
        
        newGameForm();
    }
    
    
    /**
     * The entry form for the app.  Allows user to enter a search for flickr images.
     */
    public void newGameForm() {
        Form f = new Form("Classic Flickr Concentration");
        f.setLayout(new BoxLayout(BoxLayout.Y_AXIS));
        f.addComponent(new SpanLabel("Welcome to Classic Flickr Concentration.  "
                + "A card matching game that uses flickr images."));
        f.addComponent(new SpanLabel("Begin by entering a keyword to search for matching images."));
        TextField searchField = new TextField();
        f.addComponent(searchField);
        Button searchButton = new Button("Start Game");
        searchButton.addActionListener((evt) -> {
            showBoard(searchField.getText());
        });
        
        f.addComponent(searchButton);
        f.show();
    }
    
    /**
     * Shows the game board.
     * @param search A search term sent to Flickr to get images to use for 
     * match cards.
     */
    public void showBoard(String search) {
        
        // May take a while to load images from flickr... show infinite progress
        // while that happens.
        Dialog progress = new InfiniteProgress().showInifiniteBlocking();
        
        Form f = new Form("Game");
        Container grid = new Container();
        
        // We will use GridLayout to show the cards
        grid.setLayout(new GridLayout(rows, cols));
        
        // Buttons to hold the "front" of the cards
        Button[] buttons = new Button[rows*cols];
        
        // Buttons to hold the "back" of the cards.
        Button[] backs = new Button[rows*cols];
        int index = 0;
        
        try {
            
            // Load images from flickr
            java.util.List flickrEntries = getEntriesFromFlickrService(search);
            String[] urls = new String[rows*cols];
            
            // We need half as many images as slots.. since we need pairs for matching
            for (int i=0; i<urls.length/2; i++) {
                urls[i] = (String)((Map)((Map)flickrEntries.get(i)).get("media")).get("m");
                urls[urls.length/2+i] = urls[i];
            }

            
            // Shuffle the urls for random placement of images in grid.
            java.util.List<String> shuffledUrls = Arrays.asList(urls);
            Collections.shuffle(shuffledUrls);

            // Set up the buttons for the card fronts
            // We don't add them to the form yet.
            for (String url : shuffledUrls) {

                // Create foreground for card.
                Button b = new Button(URLImage.createToStorage(cardBack, url+"-"+cardBack.getWidth(), url, URLImage.RESIZE_SCALE_TO_FILL));
                
                // Store the index of the url in the button so that we can use it later
                // to access the corresponding "back" of the button.
                b.putClientProperty("index", index);
                
                // Store the url of the image for comparison when doing matches
                b.putClientProperty("url", url);

                // We set the button to  use Label UIID to get rid of button 
                // borders
                b.setUIID("Label");
                
                b.addActionListener((evt) -> {
                    if (b.getClientProperty("match") != null) {
                        return; // already matched... don't flip.
                    }
                    
                    // Flip the card around to show the back
                    grid.replaceAndWait(b, backs[(int)b.getClientProperty("index")], new FlipTransition());
                });
                buttons[index++] = b;
            }


            // Create the buttons for the card backs
            index = 0;
            for (int i=0; i<rows; i++) {
                for (int j=0; j<cols; j++) {
                    Button l = new Button(cardBack);

                    Button front = buttons[index++];
                    
                    // Store the corresponding button for the front of the card
                    l.putClientProperty("front", front);
                    l.setUIID("Label");
                    grid.addComponent(l);
                    l.addActionListener((e) -> {
                        FlipTransition t = new FlipTransition();
                        // Flip card around to show the front of card.
                        grid.replaceAndWait(l, front, t);

                        if (currentUrl == null) {
                            // No card is currently flipped awaiting match so 
                            // set this card as the "current" card.
                            currentUrl = (String)front.getClientProperty("url");
                            currentButton = front;
                        } else {
                            
                            // This is the second card flipped.. need to compare
                            // to the "current" card
                            if (currentUrl.equals(front.getClientProperty("url"))) {
                                // We have a match.
                                
                                // Add the "match" property which is used in the 
                                // front button action listener to tell it to 
                                // not flip the card back around.
                                front.putClientProperty("match", true);
                                currentButton.putClientProperty("match", true);
                                front.getStyle().setBorder(Border.createLineBorder(2));
                                currentButton.getStyle().setBorder(Border.createLineBorder(2));

                            } else {
                                
                                // Show the card for a second then flip back 
                                // to show the backs of the two cards again
                                // because they don't match.
                                Button currButton = currentButton;
                                UITimer timer = new UITimer(() -> {
                                    grid.replace(front, l, new FlipTransition());
                                    grid.replace(currButton, (Component)backs[(int)currButton.getClientProperty("index")], new FlipTransition());

                                });

                                timer.schedule(1000, false, f);
                            }

                            
                            // Clear the currentButton and currentUrl flags
                            // since we don't have a match... the next 
                            // card tapped should be treated as first card.
                            currentButton = null;
                            currentUrl = null;
                        }


                    });
                    
                    // Add button to the "backs" array so corresponding front
                    // button can easily find its back.
                    backs[index-1] = l;
                }
            }

            f.setLayout(new BorderLayout());
            f.addComponent(BorderLayout.CENTER, grid);

            // Add button to return to new game form.
            Button newGameButton = new Button("New Game");
            newGameButton.addActionListener((evt) -> {
                newGameForm();
            });
            f.addComponent(BorderLayout.SOUTH, newGameButton);
        } finally {
            progress.dispose();
        }
        f.show();
        
    }
    

    public void stop() {
        current = Display.getInstance().getCurrent();
    }
    
    public void destroy() {
    }

    
    // Utility method to get images from flickr.
    public static java.util.List getEntriesFromFlickrService(String tag) {
        
        try {
            ConnectionRequest req = new ConnectionRequest();
            req.setUrl("http://api.flickr.com/services/feeds/photos_public.gne");
            req.setPost(false);
            req.addArgument("tagmode", "any");
            req.addArgument("tags", tag);
            req.addArgument("format", "json");
            
            NetworkManager.getInstance().addToQueueAndWait(req);
            byte[] data = req.getResponseData();
            if (data == null) {
                throw new IOException("Network Err");
            }
            JSONParser parser = new JSONParser();
            Map response = parser.parseJSON(new InputStreamReader(new ByteArrayInputStream(data), "UTF-8"));
            System.out.println("res" + response);
            java.util.List items = (java.util.List)response.get("items");
            return items;
        } catch (Exception e) {
        }
        return null;
    }
}
