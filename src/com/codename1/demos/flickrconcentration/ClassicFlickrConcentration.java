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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class ClassicFlickrConcentration {

    private Map<String,URLImage> loadedUrls = new HashMap<String, URLImage>();
    boolean isFlipping = false;
    
    public class Card extends Container {
        private Card match;
        private Button front;
        private Button back;
        private String url;
        
        
        public Card(String url) {
            this.url = url;
            URLImage img = null;
            if (loadedUrls.containsKey(this.url)) {
                img = loadedUrls.get(this.url);
            } else {
                img = URLImage.createToStorage(cardBack, url+"-"+cardBack.getWidth(), url, URLImage.RESIZE_SCALE_TO_FILL);
                loadedUrls.put(url, img);
                img.fetch();
            }
            setLayout(new BorderLayout());
            back = new Button(cardBack);
            back.setUIID("Label");
            front = new Button(img);
            front.setUIID("Label");
            front.addActionListener((evt) -> {
                flip();
            });
            back.addActionListener((evt) -> {
                flip();
            });
            addComponent(BorderLayout.CENTER, back);
        }
        
       
        
        
        
        public void flip() {
            if (isFlipping || this.getMatch() != null) {
                return;
            }
            isFlipping = true;
            boolean isFlipToFront = false;
            if (this.getComponentAt(0) == front) {
                // Flip to back
                this.replaceAndWait(front, back, new FlipTransition());
            } else {
                // Flip to front
                isFlipToFront = true;
                this.replaceAndWait(back, front, new FlipTransition());
            }
            isFlipping = false;
            
            if (isFlipToFront) {
                if (currentCard == null) {
                    currentCard = this;
                } else if (currentCard.isMatchFor(this)) {
                    match = currentCard;
                    currentCard.match = this;
                    currentCard = null;

                } else {
                    currentCard.flip();
                    this.flip();
                    currentCard = null;
                }
            }
            
        }
        
        public boolean isMatchFor(Card card) {
            return card.url.equals(this.url);
        }
        
        public Card getMatch() {
            return match;
        }
        
        
        
        
    }
    
    private Resources theme;
    
    // Stores the image for the back of a card
    private EncodedImage cardBack;
    
    // number of rows in the card grid
    private int rows = 4;
    
    // number of columns in the card grid
    private int cols = 4;
    
    // Current card
    private Card currentCard;
    
    private Card[] createCards(String keyword, int num) {
        ArrayList<Card> out = new ArrayList<Card>();
        java.util.List flickrEntries = getEntriesFromFlickrService(keyword);
        for (int i=0; i<num; i++) {
            String url = (String)((Map)((Map)flickrEntries.get(i)).get("media")).get("m");
            out.add(new Card(url));
            out.add(new Card(url));
        }
        Collections.shuffle(out);
        return out.toArray(new Card[out.size()]);
    }

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
    
    private Image[] getImagesForSearch(String query, int numImages) {
        ArrayList<Image> out = new ArrayList<Image>();
        java.util.List flickrEntries = getEntriesFromFlickrService(query);
        for (int i=0; i<numImages; i++) {
            String url = (String)((Map)((Map)flickrEntries.get(i)).get("media")).get("m");
            URLImage urlImg = URLImage.createToStorage(cardBack, url+"-"+cardBack.getWidth(), url, URLImage.RESIZE_SCALE_TO_FILL);
            urlImg.fetch();
            out.add(urlImg);
        }
        
        for (int i=0; i<numImages; i++) {
            out.add(out.get(i));
        }
        return out.toArray(new Image[out.size()]);
    }
    
    
    
    /**
     * Shows the game board.
     * @param search A search term sent to Flickr to get images to use for 
     * match cards.
     */
    public void showBoard(String search) {
        // Clear image cache
        loadedUrls.clear();
        currentCard = null;
        // May take a while to load images from flickr... show infinite progress
        // while that happens.
        Dialog progress = new InfiniteProgress().showInifiniteBlocking();
        
        Form f = new Form("Game");
        Container grid = new Container();
        
        // We will use GridLayout to show the cards
        grid.setLayout(new GridLayout(rows, cols));
        try {
            for (Card card : createCards(search, rows * cols / 2)) {
                grid.addComponent(card);
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