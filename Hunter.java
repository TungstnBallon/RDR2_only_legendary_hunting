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
    //Define
    static final String catalog = "UNK_MEMBER_0x0622EDA3";
    static final String items = "UNK_MEMBER_0xC53812FF";
    static final String acquirecosts = "UNK_MEMBER_0x4F728F50";
    static final String key = "UNK_MEMBER_0xE9806307";
    static final String costtype = "UNK_MEMBER_0xEBB42AC3";
    static final String Item =  "item";
    static final String item = "UNK_MEMBER_0xD3B360C4";
    static final String quantity = "UNK_MEMBER_0x9EA8B8F4";

    //aquirecosts keys
    static final String COST_CRAFTING_TRAPPER = "0x160953CA";
    static final String COST_CRAFTING_PEARSON = "0x2D652C21";
    static final String COST_CRAFTING_FENCE = "0xD2378EC4";
    static final String COST_SHOP_DEFAULT = "0xC96FEC6B";

    //costttypes
    static final String COST_TYPE_PRICE = "0x537EE473";
    static final String COST_TYPE_CRAFT = "0x62008E88";

    //prices
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
    //All satchels
    static final Set<String> SATCHELS = new HashSet<>(
            Arrays.asList(
                    "0x36B2ED97", "0x4E14E705", "0x5BE33D1D", "0xA1CA0708",
                    "0xAB430AE8", "0xCE847162", "0xDC99106D"
            )
    );


    //returns true if item e can be crafted at the specified job
    private static boolean filter(Element e, ST st) {

        String COST_CRAFTING;
        switch (st) {
            case FENCE -> {
                COST_CRAFTING = COST_CRAFTING_FENCE;
            }
            case TRAPPER -> {
                COST_CRAFTING = COST_CRAFTING_TRAPPER;
            }
            case PEARSON -> {
                COST_CRAFTING = COST_CRAFTING_PEARSON;
            }
            default -> throw new IllegalStateException("Unexpected value: " + st);
        }

        String keyString;
        try {
            keyString = e.getChild(acquirecosts).getChild(Item).getChildText(key);
        }
        catch (NullPointerException n) {
            return false;
        }
        return keyString.equals(COST_CRAFTING);
    }


    //removes all requierements from pearson and makes the items cost 1 Dollar
    private static void fixPearson(List<Element> itemLs) {
        List<Element> pearson = new LinkedList<>(itemLs); //copy list
        pearson.removeIf(element -> !(filter(element, ST.PEARSON))); //remove all non pearson items
        pearson.removeIf(e -> SATCHELS.contains(e.getAttributeValue("key"))); //remove satchels (they're bugged)

        for (Element buyItem : pearson) { //for every item in List pearson
           Element parent = buyItem.getChild(acquirecosts).getChild(Item).getChild(items);
           List<Element> prices = parent.getChildren();
           boolean cashprice = false;
           for (int i = 0; i < prices.size(); i++) {
               Element price = prices.get(i);
               String name = price.getChildText(item);
               if (!(name.equals(CURRENCY_CASH))) {
                   price.removeContent(price);
                   continue;
               }
               cashprice = true;
           }

           if (!cashprice) {
               Element cash = new Element(item).setText(CURRENCY_CASH);
               Element quantityElement = new Element(quantity).setAttribute("value", "100");
               Element costItem = new Element(Item);
               costItem.addContent(cash);
               costItem.addContent(quantityElement);

               parent.addContent(costItem);
           }
       }

    }


    private static boolean checkFenceTalisman(String hash) {
        switch (hash) {
            //PROVISION_TALISMAN_BUFFALO_HORN, PROVISION_TALISMAN_BEAR_CLAW, PROVISION_TALISMAN_BOAR_TUSK, PROVISION_TALISMAN_ALLIGATOR_TOOTH
            case "0x8C4CF7A2", "0xCE5DB540", "0xF4FBCF50", "0xFC449089"  -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static boolean checkFenceKeepCost(String hash) {
        switch (hash) {
            //PROVISION_CC_VINTAGE_HANDCUFFS, PROVISION_ALLIGATOR_LEGENDARY_TOOTH, PROVISION_RC_QUARTZ_CHUNK, PROVISION_BEAR_LEGENDARY_CLAW, PROVISION_RF_WOOD_COBALT, PROVISION_BOAR_TUSK_LEGENDARY, PROVISION_RS_ABALONE_SHELL_FRAGMENT, PROVISION_BUFFALO_HORN_LEGENDARY, CURRENCY_CASH
            case "0x15AC0497", "0x1959DB46", "0x7D2E6EA5", "0xA433A390", "0xC3465CFD", "0xE8B212D9", "0xE9717275", "0x858942A7", CURRENCY_CASH -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static void fixFence(List<Element> itemLs) {
        List<Element> fence = new LinkedList<>(itemLs);
        fence.removeIf(element -> (
                !(filter(element, ST.FENCE))) ||
                !(checkFenceTalisman(element.getAttributeValue("key")))
        );

        for (Element buyItem : fence) {
            Element price = buyItem.getChild(acquirecosts).getChild(Item).getChild(items);
            List<Element> costItems = price.getChildren();
            for (int i = 0; i < costItems.size(); i++) {
                boolean keep = checkFenceKeepCost(costItems.get(i).getChildText(item));
                if (!keep) {
                    if(!price.removeContent(costItems.get(i))) {
                        System.err.println("couldn't remove cost " + costItems.get(i).getChildText(item));
                    }
                }
            }
        }
    }


    private static boolean costsLegendaries(Element e) {
        List<Element> prices = e.getChild(acquirecosts).getChild(Item).getChild(items).getChildren();

        for (Element price : prices) {
            String name = price.getChildText(item);
            if (LEGENDARIES.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private static void removeNonLegendaries(List<Element> prices) {
        for (int i = 0; i < prices.size(); i++) {
            Element price = prices.get(i);
            String name = price.getChildText(item);
            if (LEGENDARIES.contains(name) || name.equals(CURRENCY_CASH)) {
                continue;
            }
            Element parent = price.getParentElement();
            parent.removeContent(price);
        }
    }

    private static void fixTrapper(List<Element> itemLs) {
        List<Element> withoutLegendaries = new LinkedList<>(itemLs);
        withoutLegendaries.removeIf(e -> !(filter(e, ST.TRAPPER)));
        List<Element> withLegendaries= new LinkedList<>(withoutLegendaries);
        withLegendaries.removeIf(e -> !(costsLegendaries(e)));
        withoutLegendaries.removeIf(Hunter::costsLegendaries);

        for (Element trapperItem : withoutLegendaries) {
            Element costs = trapperItem.getChild(acquirecosts).getChild(Item);
            costs.getChild(key).setText(COST_SHOP_DEFAULT);
            costs.getChild(costtype).setText(COST_TYPE_PRICE);

            List<Element> prices =  costs.getChild(items).getChildren();
            removeNonLegendaries(prices);
        }

        for (Element trapperItem : withLegendaries) {
            List<Element> prices =  trapperItem.getChild(acquirecosts).getChild(Item).getChild(items).getChildren();
            removeNonLegendaries(prices);
        }
    }



    public static void main(String[] args) throws IOException {
        //Load copy of base Document
        SAXBuilder saxBuilder = new SAXBuilder();
        Document catalog_sp;
        try {
            catalog_sp = saxBuilder.build("C:\\Games\\Red Dead Redemption 2\\" +
                    "lml\\test\\BASEcatalog_sp_ymt.xml").clone();
        } catch (JDOMException | IOException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            return;
        }

        //get list of all items in the game
        List<Element> itemLs = null;
        try {
            itemLs = catalog_sp.getRootElement()
                    .getChild(Hunter.catalog).getChild(items).getChildren();
        } catch (NullPointerException e) {
            System.err.println("could not find the list of all items");
        }


        fixPearson(itemLs);
        fixFence(itemLs);
        fixTrapper(itemLs);

        //write result to file
        OutputStream writer = null;
        try {
            writer = new FileOutputStream("C:\\Games\\Red Dead Redemption 2\\" +
                    "lml\\only_legendary_hunting\\OUT.xml");
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
