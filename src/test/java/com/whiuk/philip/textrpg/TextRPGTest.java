package com.whiuk.philip.textrpg;

import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.io.PrintStream;

import static org.junit.Assert.assertEquals;

public class TextRPGTest {

    private InputStream inputStream;
    private PrintStream printStream;

    @Before
    public void before() {
        inputStream = System.in;
        printStream = System.out;
    }

    @Test
    public void start_in_wheatfield() {
        TextRPG rpg = new TextRPG(inputStream, printStream);
        assertEquals("WHEATFIELD", rpg.getLocation().getName());
    }

    @Test
    public void can_move() {
        TextRPG rpg = new TextRPG(inputStream, printStream);
        rpg.processLine("MOVE SOUTH");
        assertEquals("FARMPATH", rpg.getLocation().getName());
    }

    @Test
    public void take_gets_item() {
        TextRPG rpg = new TextRPG(inputStream, printStream);
        rpg.processLine("TAKE SWORD");
        assertEquals(true, rpg.getPlayer().hasItem("SWORD"));
        assertEquals(false, rpg.getLocation().things.containsKey("SWORD"));
    }

    @Test
    public void equip_wears_item() {
        TextRPG rpg = new TextRPG(inputStream, printStream);
        rpg.processLine("TAKE SWORD");
        rpg.processLine("EQUIP SWORD");
        assertEquals(true, rpg.getPlayer().isEquipped(TextRPG.Slot.MAIN_HAND, "SWORD"));
    }

    @Test
    public void buy_item() {
        TextRPG rpg = new TextRPG(inputStream, printStream);
        rpg.processLine("MOVE SOUTH");
        rpg.processLine("MOVE WEST");
        rpg.processLine("MOVE WEST");
        rpg.processLine("MOVE SOUTH");
        rpg.processLine("MOVE SOUTH");
        assertEquals("SHOPFLOOR", rpg.getLocation().getName());
        rpg.processLine("SHOP Shopkeeper");
        rpg.processLine("BUY TINDERBOX");
        assertEquals(true, rpg.getPlayer().hasItem("TINDERBOX"));
    }
}
