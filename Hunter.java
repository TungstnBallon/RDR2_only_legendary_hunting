import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import java.io.*;
import java.util.*;


enum ST {
    TRAPPER,
    FENCE,
    PEARSON
}



public class Hunter {
    //relevant element names
    static final String catalog = "UNK_MEMBER_0x0622EDA3";
    static final String items = "UNK_MEMBER_0xC53812FF";
    static final String acquirecosts = "UNK_MEMBER_0x4F728F50";
    static final String key = "UNK_MEMBER_0xE9806307";
    static final String item = "UNK_MEMBER_0xD3B360C4";

    //relevant shoptypes
    static final String COST_CRAFTING_TRAPPER = "0x160953CA";
    static final String COST_CRAFTING_PEARSON = "0x2D652C21";
    static final String COST_CRAFTING_FENCE = "0xD2378EC4";

    //priceItems
    static final String CURRENCY_CASH = "0x7C407083";

    //All hides from legendary animals
    static final Set<String> LEGENDARIES = new HashSet<>(
            Arrays.asList(
                    "0x0DAC0E73", "0xF70C1DE0", "0x7DB8E633", "0xD4319786",
                    "0x5B21B527", "0xCB06E38D", "0x61E654EF", "0x20EA9D25",
                    "0xA5869EBC", "0xC5206B67", "0x2736D9DE", "0xE57B776F",
                    "0x78945008", "0xF9DB919D", "0xE15570B1", "0xD705C729"
            )
    );
    //All hides from legendary animals only obtainable in the epilogue
    static final Set<String> EPILOGUELEGENDARIES = new HashSet<>(
            Arrays.asList(
                    "0x5B21B527", "0xF9DB919D", "0xC5206B67"
            )
    );
    //Talismans
    static final Set<String> TALISMANS = new HashSet<>(
            Arrays.asList(
                    "0x8C4CF7A2", "0xCE5DB540", "0xF4FBCF50", "0xFC449089"
            )
    );
    //Unique items required for talismans
    static final Set<String> UNIQUEFENCEITEMS = new HashSet<>(
            Arrays.asList(
                    "0x15AC0497", "0x1959DB46", "0x7D2E6EA5", "0xA433A390",
                    "0xC3465CFD", "0xE8B212D9", "0xE9717275", "0x858942A7"
            )
    );

    //returns a list of all recipes at the specified shoptype
    private static Set<Element> getPriceItemsParents(List<Element> itemLs, ST shoptype) {
        //resolve shoptype
        String COST_CRAFTING;
        switch (shoptype) {
            case FENCE -> {
                COST_CRAFTING = COST_CRAFTING_FENCE;
            }
            case TRAPPER -> {
                COST_CRAFTING = COST_CRAFTING_TRAPPER;
            }
            case PEARSON -> {
                COST_CRAFTING = COST_CRAFTING_PEARSON;
            }
            default -> throw new IllegalStateException("Unexpected value: " + shoptype);
        }


        Set<Element> priceItemsParents = new HashSet<>();
        for (Element buyItem : itemLs) {
            //ignore non talismans for the fence
            if (shoptype == ST.FENCE) {
                String name = buyItem.getAttributeValue("key");
                if (!TALISMANS.contains(name)) {
                    continue;
                }
            }
            //get a list of the different recipes (e.g. trapper-saddles or satchels)
            List<Element> acquirecostLs = buyItem.getChild(acquirecosts).getChildren();
            if (acquirecostLs.size() == 0) {
                continue;
            }
            //add the recipe that is used at the shoptype
            for (Element acquirecost : acquirecostLs) {
                String keyString = acquirecost.getChildText(key);
                if (keyString.equals(COST_CRAFTING)) {
                    priceItemsParents.add(acquirecost.getChild(items));
                }
            }
        }
        return priceItemsParents;
    }

    //decides whether to keep a priceItem as part of a recipe
    private static boolean decideKeep(String priceItemName, ST st) {
        switch (st) {
            case FENCE -> {
                //keep if the priceItem is unique or cash
                return (UNIQUEFENCEITEMS.contains(priceItemName) || priceItemName.equals(CURRENCY_CASH));
            }
            case TRAPPER -> {
                //keep if the priceItem is a legendary pelt or cash
                return (LEGENDARIES.contains(priceItemName) || priceItemName.equals(CURRENCY_CASH));
            }
            default -> {
                throw new IllegalStateException("Unwanted argument " + st);
            }
        }
    }

    //removes unwanted recipes for a specific shoptype
    private static void removeUnwanted(List<Element> itemLs, ST shoptype) {
        Set<Element> priceItemsParents = getPriceItemsParents(itemLs, shoptype);
        switch (shoptype) {
            case PEARSON -> {
                //Remove all requirements
                for (Element priceItemsParent : priceItemsParents) {
                    priceItemsParent.removeContent();
                }
            }
            case TRAPPER, FENCE -> {
                //remove only those where decideKeep() returns false
                for (Element priceItemsParent : priceItemsParents) {
                    List<Element> priceItems = priceItemsParent.getChildren();
                    for (int i = 0; i < priceItems.size(); ) {
                        Element priceItem = priceItems.get(i);
                        String name = priceItem.getChildText(item);
                        if (decideKeep(name, shoptype)) {
                            i++;
                        } else {
                            priceItemsParent.removeContent(priceItem);
                        }
                    }
                }
            }
            default -> {
                throw new IllegalStateException("Unexpected state " + shoptype);
            }
        }
    }


    public static void main(String[] args) throws IOException {
        String usage = "Arguments: PATH_TO_INPUT_FILE.xml PATH_TO_OUTPUT_FILE.xml";

        //Parse arguments
        if (args.length != 2) {
            throw new IllegalArgumentException(usage);
        }
        String inPath = args[0];
        String outPath = args[1];

        //Load copy of base Document
        SAXBuilder saxBuilder = new SAXBuilder();
        Document catalog_sp;
        try {
            catalog_sp = saxBuilder.build(inPath).clone();
        } catch (JDOMException | IOException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            return;
        }


        //Uncomment the line below to remove hides only obtainable in the Epilogue from the Trapper
        //LEGENDARIES.removeAll(EPILOGUELEGENDARIES);


        //get list of all items in the game
        List<Element> itemLs = null;
        try {
            itemLs = catalog_sp.getRootElement()
                    .getChild(Hunter.catalog).getChild(items).getChildren();
        } catch (NullPointerException e) {
            System.err.println("could not find the list of all items");
        }


        //remove unwanted recipes from Trapper, Fence and Pearson
        for (ST st : ST.values()) {
            removeUnwanted(itemLs, st);
        }


        //write result to file
        OutputStream writer = null;
        try {
            writer = new FileOutputStream(outPath);
        } catch (FileNotFoundException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
        if (writer == null) {
            return;
        }

        XMLOutputter xmlOutputter = new XMLOutputter();
        xmlOutputter.setFormat(Format.getPrettyFormat());
        xmlOutputter.output(catalog_sp, writer);
    }
}
