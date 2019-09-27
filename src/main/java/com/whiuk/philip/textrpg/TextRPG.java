package com.whiuk.philip.textrpg;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.whiuk.philip.textrpg.TextRPG.Skill.DODGING;
import static com.whiuk.philip.textrpg.TextRPG.Skill.UNARMED;
import static com.whiuk.philip.textrpg.TextRPG.Stat.STRENGTH;

@SuppressWarnings({"unchecked", "SuspiciousMethodCalls"})
class TextRPG implements Runnable {
    interface Command {
        void perform(String arguments);
    }
    interface Thing {
        String getName();
        String getNameStatus();
        String getDescription();
    }
    interface Openable {
        boolean canOpen();
        String whyCantOpen();
        void open();
    }
    interface Lockable {
        boolean canUnlock();
        String whyCantUnlock();
        void unlock();
    }
    interface Reactive {
        void onArrive();
    }
    interface Shop {
        List<ShopItem> viewShop();

        Item buyItem(String arguments);

        boolean hasItem(String itemName);

        ShopItem getItem(String itemName);
    }

    @SuppressWarnings("unused")
    enum Slot {
        MAIN_HAND("Main-Hand"), OFF_HAND("Off-Hand"),
        HEAD("Head"), CHEST("Chest"), LEGS("Legs"),
        HANDS("Hands"), BACK("Back");

        private String name;
        Slot(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    @SuppressWarnings("unused")
    enum Skill {
        SWORDS("Swords"), AXES("Axes"), UNARMED("Unarmed"),
        DODGING("Dodging");

        private final String name;
        Skill(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    abstract class UseExit {
        void tryMove(Exit exit) {
            if (exit.canExit()) {
                location = exit.getLocation();
                printLine(location.getDescription());
                location.arrive();
            } else {
                printLine(exit.whyCantExit());
            }
        }
    }
    abstract class ThingInteraction {
        Thing findThing(String name) {
            return location.getThing(name);
        }
    }

    class MoveCommand extends UseExit implements Command {
        Map<String, Direction> directions = new HashMap<>();

        MoveCommand() {
            directions.put("NORTH", Direction.NORTH);
            directions.put("N", Direction.NORTH);
            directions.put("EAST", Direction.EAST);
            directions.put("E", Direction.EAST);
            directions.put("SOUTH", Direction.SOUTH);
            directions.put("S", Direction.SOUTH);
            directions.put("WEST", Direction.WEST);
            directions.put("W", Direction.WEST);
        }

        public void perform(String directionText) {
            Direction direction = directions.get(directionText);
            if (direction == null) {
                printLine("You can't move like that");
            } else if (location.hasExit(direction)) {
                tryMove(location.getExit(direction));
            } else {
                printLine("You can't move " + direction);
            }
        }
    }
    class ClimbCommand extends UseExit implements Command {
        Map<String, Direction> climbableDirections = new HashMap<>();

        ClimbCommand() {
            climbableDirections.put("UP", Direction.UP);
            climbableDirections.put("U", Direction.UP);
            climbableDirections.put("DOWN", Direction.DOWN);
            climbableDirections.put("D", Direction.DOWN);
        }

        @Override
        public void perform(String directionText) {
            Direction direction = climbableDirections.get(directionText);
            if (direction == null) {
                printLine("You can't climb like that");
            } else if (location.hasExit(direction)) {
                tryMove(location.getExit(direction));
            } else {
                printLine("There's nothing for you to climb "+direction);
            }
        }
    }
    class DescribeCommand implements Command {

        public void perform(String target) {
            if (target.length() == 0) {
                printLine(location.getDescription());
            } else if (location.things.containsKey(target)) {
                printLine(location.things.get(target).getDescription());
            }
        }
    }
    class QuitCommand implements Command {

        public void perform(String arguments) {
            quit = true;
        }
    }
    class UnknownCommand implements Command {

        public void perform(String command) {
            printLine("Unknown command: " + command);
        }
    }
    class OpenCommand extends ThingInteraction implements Command {

        @Override
        public void perform(String arguments) {
            Thing thing = findThing(arguments);
            if (thing instanceof Openable) {
                Openable openableThing = (Openable) thing;
                if (openableThing.canOpen()) {
                    openableThing.open();
                    printLine("You open the "+thing.getName());
                } else {
                    printLine(openableThing.whyCantOpen());
                }
            } else {
                printLine("You cam't open that");
            }
        }
    }
    class UnlockCommand extends ThingInteraction implements Command {
        @Override
        public void perform(String arguments) {
            Thing thing = findThing(arguments);
            if (thing instanceof Lockable) {
                Lockable lockableThing = (Lockable) thing;
                if (lockableThing.canUnlock()) {
                    lockableThing.unlock();
                    printLine("You unlock the "+thing.getName());
                } else {
                    printLine(lockableThing.whyCantUnlock());
                }
            } else {
                printLine("You cam't unlock that");
            }
        }
    }
    class AttackCommand extends ThingInteraction implements Command {
        @Override
        public void perform(String arguments) {
            Thing thing = findThing(arguments);
            if (thing == null) {
                printLine("Can't see "+arguments);
            } else if (thing instanceof NPC) {
                NPC npc = (NPC) thing;
                if (npc.isAlive()) {
                    npc.doCombatRound();
                } else {
                    printLine("The "+npc.name+" is already dead");
                }
            } else {
                printLine("You cam't attack that");
            }
        }
    }
    class KillCommand extends ThingInteraction implements Command {
        @Override
        public void perform(String arguments) {
            Thing thing = findThing(arguments);
            if (thing == null) {
                printLine("Can't see "+arguments);
            } else if (thing instanceof NPC) {
                NPC npc = (NPC) thing;
                if (((NPC) thing).isAlive()) {
                    while (player.isAlive() && npc.isAlive()) {
                        npc.doCombatRound();
                    }
                } else {
                    printLine("The "+npc.name+" is already dead");
                }
            } else {
                printLine("You cam't attack that");
            }
        }
    }
    class TalkCommand extends ThingInteraction implements Command {

        @Override
        public void perform(String arguments) {
            String[] argumentsData = arguments.split(" ", 2);
            if (argumentsData[0].equals("TO")) {
               talkTo(argumentsData[1]);
            } else if (currentDialog != null) {
                continueConversation(arguments);
            } else {
                printLine("Need to TALK TO someone first");
            }
        }

        private void talkTo(String npcName) {
            NPC npc = (NPC) findThing(npcName);
            if (npc != null) {
                if (npc.talks) {
                    currentDialogNPC = npc;
                    currentDialog = npc.openingDialog;
                    if (currentDialog == null) {
                        printLine("No opening dialog for " + npc.name);
                    } else {
                        npc.doConversation(currentDialog);
                    }
                } else {
                    printLine("The "+npc.name + " doesn't look interested in talking");
                }
            } else {
                printLine("Can't see "+npcName + " to talk to them");
            }
        }

        private void continueConversation(String arguments) {
            currentDialog.nextAction.processInput(arguments);
        }
    }
    class TakeCommand extends ThingInteraction implements Command {
        @Override
        public void perform(String arguments) {
            Thing thing = findThing(arguments);
            if (thing == null) {
                printLine("There's no "+arguments+" to take");
            } else if (thing instanceof Item) {
                Item item = (Item) thing;
                if (player.hasSpaceFor(item)) {
                    player.take(item);
                    location.things.remove(item.name);
                    printLine("You take the "+thing.getName());
                } else {
                    printLine("No space for the "+thing.getName());
                }
            } else {
                printLine("You cam't take that");
            }
        }
    }
    class DropCommand extends ThingInteraction implements Command {
        @Override
        public void perform(String name) {
            Optional<Item> itemFind = player.inventory.stream().filter(item -> item.name.equals(name)).findFirst();
            if (itemFind.isPresent()) {
                Item item = itemFind.get();
                player.drop(item);
                location.things.put(item.name, item);
                printLine("You take the "+item.getName());
            } else {
                printLine("You don't have a "+name+" to drop.");
            }
        }
    }
    class EquipCommand implements Command {
        @Override
        public void perform(String name) {
            Optional<Item> itemFind = player.inventory.stream().filter(item -> item.name.equals(name)).findFirst();
            if (itemFind.isPresent()) {
                Item item = itemFind.get();
                Optional<String> reasonUnableToEquip = player.canEquip(item);
                if (!reasonUnableToEquip.isPresent()) {
                    Equipment removedItem = player.equip((Equipment) item);
                    if (removedItem != null) {
                        printLine("You take off the "+removedItem.getName());
                    }
                    printLine("You equip the "+item.getName());
                } else {
                    printLine("You can't equip the "+item.getName() + ": " + reasonUnableToEquip.get());
                }
                location.things.put(item.name, item);
            } else {
                printLine("You don't have a "+name+" to drop.");
            }
        }
    }
    class LootCommand extends ThingInteraction implements Command {
        @Override
        public void perform(String arguments) {
            Thing thing = findThing(arguments);
            if (thing == null) {
                printLine("Can't see "+arguments);
            } else if (thing instanceof NPC) {
                NPC npc = (NPC) thing;
                if (((NPC) thing).isAlive()) {
                    printLine("You can't loot them while they are alive!");
                } else {
                    boolean playerHasNoSpace = false;
                    List<Item> loot = npc.loot;
                    if (loot.isEmpty()) {
                        printLine("There's nothing to take!");
                    } else {
                        printLine("You start looting the "+npc.name);
                        while (!loot.isEmpty() && !playerHasNoSpace) {
                            Item item = loot.get(0);
                            if (player.hasSpaceFor(item)) {
                                loot.remove(item);
                                player.take(item);
                                printLine("You take a "+item.name);
                            } else {
                                playerHasNoSpace = true;
                            }
                        }
                        if (playerHasNoSpace) {
                            printLine("You don't have any more space for items");
                        }
                    }
                }
            } else {
                printLine("You cam't attack that");
            }
        }
    }
    class InventoryCommand implements Command {

        @Override
        public void perform(String arguments) {
            printLine("Inventory:");
            if (player.inventory.isEmpty()) {
                printLine("No items");
            } else {
                player.inventory.forEach(item -> printLine(item.name + ": " + item.description));
            }
        }
    }
    class EquipmentCommand implements Command {

        @Override
        public void perform(String arguments) {
            printLine("Equipment:");
            if (player.equipment.isEmpty()) {
                printLine("No equipment");
            } else {
                player.equipment.forEach((slot,item) -> printLine(slot.toString() + " - " + item.getName() + " : " + item.description));
            }
        }
    }
    class StatsCommand implements Command {

        @Override
        public void perform(String arguments) {
            printLine("Stats:");
            if (player.stats.isEmpty()) {
                printLine("No stats");
            } else {
                player.stats.forEach((stat, exp) -> printLine(stat.toString() + " - " + exp.level + " : " + exp.experience));
            }
        }
    }
    class SkillsCommand implements Command {

        @Override
        public void perform(String arguments) {
            printLine("Skills:");
            if (player.stats.isEmpty()) {
                printLine("No skills");
            } else {
                player.skills.forEach((stat, exp) -> printLine(stat.toString() + " - " + exp.level + " : " + exp.experience));
            }
        }
    }
    class ShopCommand implements Command {

        @Override
        public void perform(String shopName) {
            if (location.things.containsKey(shopName)) {
                if (location.things.get(shopName) instanceof Shop) {
                    Shop shop = (Shop) location.things.get(shopName);
                    List<ShopItem> items = shop.viewShop();
                    currentShop = shop;
                    if (items.isEmpty()) {
                        printLine("The shop doesn't have anything to sell!");
                    } else {
                        items.forEach(item -> printLine(item.getShopEntry()));
                    }
                }
            }
        }
    }
    class BuyCommand implements Command {

        @Override
        public void perform(String itemName) {
            if (currentShop == null || !location.things.containsValue(currentShop)) {
                printLine("View the shop listing first!");
            } else {
                if (currentShop.hasItem(itemName)) {
                    ShopItem shopItem = currentShop.getItem(itemName);
                    //TODO: Haggling
                    if (player.money > shopItem.price) {
                        if (player.hasSpaceFor(shopItem.item)) {
                            player.money -= shopItem.price;
                            Item item = currentShop.buyItem(itemName);
                            player.take(item);
                        } else {
                            printLine("You don't have enough space for that");
                        }
                    } else {
                        printLine("You don't have enough money for that");
                    }
                } else {
                    printLine("The shop doesn't have any of that item.");
                }
            }
        }
    }

    enum Direction {
        NORTH, EAST, SOUTH, WEST, UP, DOWN;

        public static Direction opposite(Direction direction) {
            if (direction == NORTH) return SOUTH;
            if (direction == SOUTH) return NORTH;
            if (direction == EAST) return WEST;
            if (direction == WEST) return EAST;
            if (direction == UP) return DOWN;
            if (direction == DOWN) return UP;
            throw new IllegalArgumentException();
        }
}
    enum Stat {
        INITATIVE, STRENGTH
    }

    private static final int[] levels = new int[]{0,83, 172, 250, 400, 650, 1000};

    class Experience {
        int level;
        int experience;
        Experience(int exp) {
            experience = exp;
            while(levels[level+1] < experience) {
                level += 1;
            }
        }

        void gainExperience(int exp) {
            experience += exp;
            while(levels[level+1] < experience) {
                level += 1;
            }
        }
    }

    abstract class GameCharacter {
        Map<Stat, Experience> stats = new HashMap<>();
        private int health;

        GameCharacter(int health) {
            this.health = health;
        }

        int stat(Stat stat) {
            return this.stats.getOrDefault(stat, new Experience(0)).level;
        }

        //TODO: Targeted damage
        @SuppressWarnings("unused")
        public abstract int defense();

        public void takeDamage(int damage) {
            health -= damage;
            if (health <= 0) {
                die();
            }
        }

        public abstract void die();

        public boolean isAlive() {
            return health > 0;
        }
    }

    class Location {
        private final String name;
        private final String baseDescription;
        Map<Direction,Exit> exits = new HashMap<>();
        Map<String,Thing> things = new HashMap<>();

        Location(String name, String baseDescription) {
            this.name = name;
            this.baseDescription = baseDescription;
        }

        String getName() {
            return name;
        }

        void addExit(Direction direction, Exit exit) {
            this.exits.put(direction, exit);
        }

        void setThings(Map<String, Thing> things) {
            this.things = new HashMap<>(things);
        }

        Exit getExit(Direction direction) {
            return exits.get(direction);
        }

        public boolean hasExit(Direction direction) {
            return exits.containsKey(direction);
        }

        public String getDescription() {
            String exitDescription = exits.size() < 1 ? "" : System.lineSeparator() +
                    exits.values().stream()
                            .map(Exit::getDescription).collect(Collectors
                            .joining(System.lineSeparator()));
            String thingsDescription = things.size() < 1 ? "" : System.lineSeparator() +
                    "Around you is: " +
                    things.values().stream()
                            .map(Thing::getNameStatus).collect(Collectors
                            .joining(", "));

            return baseDescription + thingsDescription + exitDescription;
        }

        public Thing getThing(String name) {
            return things.get(name);
        }

        public void arrive() {
            things.values().stream()
                    .filter(t -> t instanceof Reactive)
                    .map(t -> (Reactive) t)
                    .forEach(Reactive::onArrive);
        }
    }
    class Exit {
        Location location;
        String baseDescription;

        Exit(Location location, String baseDescription) {
            this.location = location;
            this.baseDescription = baseDescription;
        }

        public Location getLocation() {
            return location;
        }

        public String getDescription() {
            return baseDescription;
        }

        public boolean canExit() {
            return true;
        }

        public String whyCantExit() {
            return "";
        }
    }
    class Door implements Thing, Openable, Lockable {
        private Location location;
        private Direction direction;
        private Location location2;
        private Exit exitTo1;
        private Exit exitTo2;
        private boolean isOpen;
        private String name;
        private boolean locked;
        private String description;

        Door(String name, String description, Location location1, Location location2, Direction direction,
             String descriptionTo2, String descriptionTo1, boolean open) {
            this.name = name;
            this.description = description;
            this.location = location1;
            this.location2 = location2;
            this.direction = direction;
            this.isOpen = open;

            exitTo1 = new Exit(location1, descriptionTo1) {
                public boolean canExit() {
                    return isOpen;
                }

                public String whyCantExit() {
                    return "The door is shut";
                }
            };
            exitTo2 = new Exit(location2, descriptionTo2) {
                public boolean canExit() {
                    return isOpen;
                }

                public String whyCantExit() {
                    return "The door is shut";
                }
            };
        }


        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getNameStatus() {
            return name + (locked ? " (locked)" : isOpen ? " (open)" : " (closed)");
        }

        @Override
        public String getDescription() {
            return description + "It is currently " + (locked ? "locked." : isOpen ? "open." : "closed.");
        }

        Direction getExitDirectionTo(Location location) {
            if (location == this.location2) {
                return direction;
            } else if (location == this.location) {
                return Direction.opposite(direction);
            } else {
                throw new IllegalArgumentException();
            }
        }

        Exit getExitTo(Location location) {
            if (location == this.location2) {
                return exitTo2;
            } else if (location == this.location) {
                return exitTo1;
            } else {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public boolean canOpen() {
            return !locked;
        }

        @Override
        public String whyCantOpen() {
            return locked ? "The door is locked" : "The door can't be opened";
        }

        @Override
        public void open() {
            isOpen = true;
        }

        @Override
        public boolean canUnlock() {
            return locked; //TODO: Keys
        }

        @Override
        public String whyCantUnlock() {
            if (locked) {
                return "You don't have the right key";
            } else {
                return "It's not locked!";
            }
        }

        @Override
        public void unlock() {
            locked = false;
        }
    }
    class CombatData {
        boolean aggressive;
        int damageMin;
        int damageMax;
        int attackSkill;
        int dodgeSkill;

        CombatData(boolean aggressive, int damageMin, int damageMax, int attackSkill, int dodgeSkill) {
            this.aggressive = aggressive;
            this.damageMin = damageMin;
            this.damageMax = damageMax;
            this.attackSkill = attackSkill;
            this.dodgeSkill = dodgeSkill;
        }
    }
    class NPC extends GameCharacter implements Thing, Reactive {

        private final String name;
        private String description;
        private final CombatData combatData;
        private boolean talks;
        private Dialogue openingDialog;
        public List<Item> loot;

        NPC(String name, String description,
            int health, CombatData combatData,
            boolean talks, Dialogue openingDialog, List<Item> loot) {
            super(health);
            this.name = name;
            this.description = description;
            this.combatData = combatData;
            this.talks = talks;
            this.openingDialog = openingDialog;
            this.loot = loot;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getNameStatus() {
            return name + (isAlive() ? "" : " (dead)");
        }

        @Override
        public String getDescription() {
            return description + (isAlive() ? "" : " (dead)");
        }

        @Override
        public void onArrive() {
            if (combatData.aggressive && playerIsNotThreat()) {
                startFight();
            }
        }

        private boolean playerIsNotThreat() {
            return false;
        }

        private void startFight() {
            printLine("The " + getName() + " moves towards you");
            doCombatRound();
        }

        private void doCombatRound() {
            if (stat(Stat.INITATIVE) > player.stat(Stat.INITATIVE)) {
                attackPlayer();
                if (player.isAlive()) {
                    resolvePlayerAttack();
                }
            } else {
                resolvePlayerAttack();
                if (isAlive()) {
                    attackPlayer();
                }
            }
        }

        private void attackPlayer() {
            Random random = new Random();
            int hitChance = random.nextInt(100) + combatData.attackSkill - player.skill(DODGING);
            if (hitChance > 50) {
                int strike = random.nextInt(combatData.damageMax - combatData.damageMin + 1) + combatData.damageMin;
                int damage = strike - player.defense();
                printLine("The "+this.name+" hits you for "+ damage);
                player.takeDamage(damage);
            } else {
                printLine("The "+this.name+" misses you");
            }
        }

        private void resolvePlayerAttack() {
            Random random = new Random();
            //TODO: Dual-wield
            Weapon weapon = (Weapon) player.equipment.get(Slot.MAIN_HAND);
            boolean usingWeapon = weapon != null;
            Skill skill = usingWeapon ? weapon.weaponSkill : UNARMED;
            int hitChance = random.nextInt(100) + player.skill(skill) - combatData.dodgeSkill;

            if (hitChance > 50) {
                int strike;
                int strength = player.stat(STRENGTH);
                if (usingWeapon) {
                    int weaponSkill = player.skill(weapon.weaponSkill);
                    //TODO: More complex weapon damage calculations
                    strike = strength * weaponSkill * random.nextInt(weapon.damageMax - weapon.damageMin +1 ) + weapon.damageMin;
                } else {
                    int unarmedSkill = player.skill(UNARMED);
                    strike = unarmedSkill * random.nextInt(2)+1;
                }
                int damage = strike - defense();
                printLine("You hit the "+this.name+" for "+ damage);
                takeDamage(damage);
                player.gainSkillExp(skill, damage*4);
            } else {
                printLine("You miss the "+this.name);
            }
        }

        @Override
        public int defense() {
            return 0;
        }

        @Override
        public void die() {
            printLine("The "+this.name+" dies");
        }

        public void doConversation(Dialogue dialogue) {
            if (dialogue.playerText != null) {
                printLine("\"" + dialogue.playerText + "\"");
            }
            printLine(name + ": \"" + dialogue.characterText + "\"");
            if (dialogue.nextAction != null) {
                dialogue.nextAction.perform();
            } else {
                currentDialog = null;
                currentDialogNPC = null;
            }
        }
    }
    class Shopkeeper extends NPC implements Shop {

        private final List<ShopItem> shopItems;

        Shopkeeper(String name, String description, int health, CombatData combatData,
                   boolean talks, Dialogue openingDialog, List<Item> loot, List<ShopItem> shopItems) {
            super(name, description, health, combatData, talks,  openingDialog, loot);
            this.shopItems = shopItems;
        }

        @Override
        public List<ShopItem> viewShop() {
            return shopItems;
        }

        @Override
        public Item buyItem(String itemName) {
            Iterator<ShopItem> i = shopItems.iterator();
            while(i.hasNext()) {
                ShopItem shopItem = i.next();
                if (shopItem.item.name.equals(itemName)) {
                    if (shopItem.quantity > 1) {
                        shopItem.quantity -= 1;
                    } else {
                        i.remove();
                    }
                    return shopItem.item;
                }
            }
            return null;
        }

        @Override
        public boolean hasItem(String itemName) {
            return shopItems.stream().anyMatch(i -> i.item.name.equals(itemName));
        }

        @Override
        public ShopItem getItem(String itemName) {
            //noinspection ConstantConditions
            return shopItems.stream().filter(i -> i.item.name.equals(itemName)).findFirst().get();
        }
    }
    class Player extends GameCharacter {
        private ArrayList<Item> inventory = new ArrayList<>();
        private Map<Slot, Equipment> equipment = new HashMap<>();
        private Map<Skill, Experience> skills = new HashMap<>();
        public int money;

        Player(int health, int money) {
            super(health);
            this.money = money;
        }

        @Override
        public int defense() {
            return equipment.values().stream().mapToInt(e -> e.defense).sum();
        }

        @Override
        public void die() {
            printLine("Oh no! You died");
        }

        public boolean hasSpaceFor(@SuppressWarnings("unused") Item item) {
            //TODO: Volume & Space, Bags
            //TODO: Weight or just encumbered
            return inventory.size() < 28;
        }

        public void take(Item item) {
            inventory.add(item);
        }

        public void drop(Item item) {
            inventory.remove(item);
        }

        public Optional<String> canEquip(Item item) {
            if (!(item instanceof Equipment)) {
                return Optional.of("That item can't be equipped");
            }
            Equipment itemToEquip = (Equipment) item;
            if (equipment.containsKey(itemToEquip.slot)) {
                if (!hasSpaceFor(equipment.get(itemToEquip.slot))) {
                    return Optional.of("You don't have space for the item you're removing");
                }
            }
            for (SkillRequirement skillReq : itemToEquip.skillRequirements) {
                if (player.skill(skillReq.skill) < skillReq.level) {
                    return Optional.of("You need level "+skillReq.level + " in "+skillReq.skill + " to equip that item");
                }
            }
            return Optional.empty();
        }

        public Equipment equip(Equipment item) {
            inventory.remove(item);
            //TODO: two-handed weapons
            Equipment removedItem = equipment.put(item.getSlot(), item);
            if (removedItem != null) {
                inventory.add(removedItem);
            }
            return removedItem;
        }

        public int skill(Skill skill) {
            return this.skills.getOrDefault(skill, new Experience(0)).level;
        }

        public void gainSkillExp(Skill skill, int exp) {
            if (skills.containsKey(skill)) {
                skills.get(skill).gainExperience(exp);
            } else {
                skills.put(skill, new Experience(exp));
            }
        }

        public boolean hasItem(String itemName) {
            return inventory.stream().anyMatch(i -> i.name.equals(itemName));
        }

        public boolean isEquipped(Slot slot, String item) {
            return equipment.containsKey(slot) && equipment.get(slot).getName().equals(item);
        }
    }
    private class Item implements Thing {
        private final String name;
        public final String description;

        Item(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getNameStatus() {
            return name; //TODO: Quality, Damage
        }

        @Override
        public String getDescription() {
            return description;
        }
    }
    private class ShopItem {
        Item item;
        int quantity;
        int price;

        ShopItem(Item item, int quantity, int price) {
            this.item = item;
            this.quantity = quantity;
            this.price = price;
        }

        String getName() {
            return item.getName();
        }

        String getNameStatus() {
            return item.getNameStatus();
        }

        String getDescription() {
            return item.description;
        }

        public String getShopEntry() {
            return getNameStatus() + " (" + quantity + ") - " + price + " coin(s)";
        }
    }
    private class Equipment extends Item {
        private final Slot slot;
        public final List<SkillRequirement> skillRequirements;
        public int defense;

        Equipment(String name, String description, Slot slot,
                  int defense,
                  List<SkillRequirement> skillRequirements) {
            super(name, description);
            this.slot = slot;
            this.skillRequirements = new ArrayList<>(skillRequirements);
        }

        public Slot getSlot() {
            return slot;
        }
    }
    private class SkillRequirement {
        final Skill skill;
        final int level;

        private SkillRequirement(Skill skill, int level) {
            this.skill = skill;
            this.level = level;
        }
    }
    private class Weapon extends Equipment {
        int damageMin;
        int damageMax;
        Skill weaponSkill;

        private Weapon(String name, String description, Slot slot, List<SkillRequirement> skillRequirements,
                       int min, int max, Skill weaponSkill) {
            super(name, description, slot, 0, skillRequirements);
            this.weaponSkill = weaponSkill;
            damageMin = min;
            damageMax = max;
        }
    }
    private class Dialogue {
        private String playerText;
        private String characterText;
        private DialogueAction nextAction;

        Dialogue(String playerText, String characterText, DialogueAction nextAction) {
            this.playerText = playerText;
            this.characterText = characterText;
            this.nextAction = nextAction;
        }
    }
    interface DialogueAction {
        void perform();

        void processInput(String arguments);
    }
    class DialogueChoice implements DialogueAction {
        List<String> dialogOptions = new ArrayList<>();

        void addOption(String option) {
            dialogOptions.add(option);
        }

        @Override
        public void perform() {
            for (int i = 0; i < dialogOptions.size(); i++) {
                printLine("["+i+"] "+dialogue.get(dialogOptions.get(i)).playerText);
            }
        }

        @Override
        public void processInput(String arguments) {
            currentDialog = dialogue.get(dialogOptions.get(Integer.parseInt(arguments)));
            currentDialogNPC.doConversation(currentDialog);
        }
    }

    public static void main(String[] args) {
        new TextRPG(System.in, System.out).run();
    }

    private PrintStream printStream;
    private Map<String, Command> commands;

    private Command unknownCommand = new UnknownCommand();
    private Map<String, Location> locations;
    private Map<String, Dialogue> dialogue;
    private Player player;
    private Location location;
    private Dialogue currentDialog;
    private NPC currentDialogNPC;
    private Shop currentShop;

    private BufferedReader bufferedReader;
    private boolean quit = false;

    TextRPG(InputStream inputStream, PrintStream printStream) {
        this.printStream = printStream;
        loadWorld();
        player = new Player(10, 10);
        registerCommands();
        bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    private void loadWorld() {
        JSONParser jsonParser = new JSONParser();
        try (FileReader reader = new FileReader("world.json")) {
            JSONObject worldData = (JSONObject) jsonParser.parse(reader);

            locations = new HashMap<>();
            JSONArray locationsJson = (JSONArray) worldData.get("locations");
            locationsJson.forEach(lJson -> {
                Location location = parseLocation((JSONObject) lJson);
                locations.put(location.getName(), location);
            });

            Map<String, DialogueChoice> dialogueChoices = new HashMap<>();
            JSONArray dialogueChoicesJson = (JSONArray) worldData.get("dialogueChoices");
            dialogueChoicesJson.forEach(dcj -> {
                JSONObject dcjo = (JSONObject) dcj;
                DialogueChoice choice = new DialogueChoice();
                ((JSONArray) dcjo.get("options")).forEach(option -> choice.addOption((String) option));
                dialogueChoices.put((String) dcjo.get("name"), choice);
            });

            dialogue = new HashMap<>();
            JSONArray dialogueJson = (JSONArray) worldData.get("dialogue");
            dialogueJson.forEach(dj -> {
                JSONObject djo = (JSONObject) dj;
                DialogueAction nextAction = null;
                switch ((String) djo.get("nextActionType")) {
                    case "CHOICE":
                        nextAction = dialogueChoices.get(djo.get("choices"));
                }
                Dialogue d = new Dialogue((String) djo.get("playerText"), (String) djo.get("characterText"), nextAction);
                dialogue.put((String) djo.get("name"), d);
            });


            Map<String, Thing> things = new HashMap<>();
            JSONArray thingsJson = (JSONArray) worldData.get("things");
            thingsJson.forEach(thingJson -> {
                JSONObject thingJO = (JSONObject) thingJson;
                Thing thing = parseThing(thingJO);
                things.put(thing.getName(), thing);
            });

            JSONArray exitsJson = (JSONArray) worldData.get("exits");
            exitsJson.forEach(eJson -> {
                JSONObject exit = (JSONObject) eJson;
                Location to;
                switch ((String) exit.get("type")) {
                    case "DEFAULT":
                        Direction direction = Direction.valueOf((String) exit.get("direction"));
                        to = locations.get(exit.get("to"));
                        Exit e = new Exit(to, (String) exit.get("description"));
                        locations.get(exit.get("from")).addExit(direction, e);
                        break;
                    case "DOOR":
                        Door door = (Door) things.get(exit.get("door"));
                        to = locations.get(exit.get("to"));
                        locations.get(exit.get("from")).addExit(door.getExitDirectionTo(to), door.getExitTo(to));

                }
            });

            JSONArray locationThingsJson = (JSONArray) worldData.get("locationThings");
            locationThingsJson.forEach(ltJson -> {
                String locationName = (String) ((JSONObject) ltJson).get("name");
                JSONArray thingsLocationJ = (JSONArray) ((JSONObject) ltJson).get("things");
                Map<String, Thing> thingsLocation = new HashMap<>();
                thingsLocationJ.forEach(tJ -> {
                    String thingName = (String) tJ;
                    Thing thing = things.get(thingName);
                    if (thing == null) {
                        throw new IllegalArgumentException("Unknown thing: " + thingName);
                    }
                    thingsLocation.put(thingName, thing);
                });
                Location location = locations.get(locationName);
                if (location == null) {
                    throw new IllegalArgumentException("Bad location: " + locationName);
                }
                location.setThings(thingsLocation);
            });

            location = locations.get(worldData.get("startLocation"));

        } catch (IOException | ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private Thing parseThing(JSONObject json) {
        String type = (String) json.get("type");
        switch(type) {
            case "Door":
                return parseDoor(json, locations);
            case "NPC":
                return parseNPC(json, dialogue);
            case "Shopkeeper":
                return parseShopkeeper(json, dialogue);
            case "Weapon":
                return parseWeapon(json);
            case "Equipment":
                return parseEquipment(json);
            case "Item":
                return parseItem(json);
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    private Location parseLocation(JSONObject json) {
        return new Location((String) json.get("name"), (String) json.get("baseDescription"));
    }

    private NPC parseNPC(JSONObject json, Map<String, Dialogue> dialogue) {
        try {
            Dialogue openingDialog = dialogue.get(json.get("openingDialogue"));
            ArrayList<Item> loot = new ArrayList<>();
            ((JSONArray) json.getOrDefault("loot", new JSONArray()))
                    .forEach(item -> loot.add((Item) parseThing((JSONObject) item)));
            return new NPC(
                    (String) json.get("name"),
                    (String) json.get("description"),
                    ((Long) json.get("health")).intValue(),
                    parseCombatData((JSONObject) json.get("combatData")),
                    (Boolean) json.get("talks"),
                    openingDialog,
                    loot);
        } catch (Exception e) {
            throw new RuntimeException("Unable to parse NPC: "+ json.toJSONString(), e);
        }
    }

    private CombatData parseCombatData(JSONObject json) {
        return new CombatData(
                (Boolean) json.get("aggressive"),
                ((Long) json.get("damageMin")).intValue(),
                ((Long) json.get("damageMax")).intValue(),
                ((Long) json.get("attackSkill")).intValue(),
                ((Long) json.get("dodgeSkill")).intValue());
    }

    private Shopkeeper parseShopkeeper(JSONObject json, Map<String, Dialogue> dialogue) {
        try {
            Dialogue openingDialog = dialogue.get(json.get("openingDialogue"));
            ArrayList<Item> loot = new ArrayList<>();
            ((JSONArray) json.getOrDefault("loot", new JSONArray()))
                    .forEach(item -> loot.add((Item) parseThing((JSONObject) item)));
            ArrayList<ShopItem> shopItems = new ArrayList<>();
            ((JSONArray) json.getOrDefault("shopItems", new JSONArray()))
                    .forEach(item -> shopItems.add(parseShopItem((JSONObject) item)));
            return new Shopkeeper(
                    (String) json.get("name"),
                    (String) json.get("description"),
                    ((Long) json.get("health")).intValue(),
                    parseCombatData((JSONObject) json.get("combatData")),
                    (Boolean) json.get("talks"),
                    openingDialog,
                    loot,
                    shopItems);
        } catch (Exception e) {
            throw new RuntimeException("Unable to parse NPC: "+ json.toJSONString(), e);
        }
    }

    private ShopItem parseShopItem(JSONObject json) {
        return new ShopItem(
            (Item) parseThing((JSONObject) json.get("item")),
            ((Long) json.get("quantity")).intValue(),
            ((Long) json.get("price")).intValue()
        );
    }

    private Door parseDoor(JSONObject json, Map<String, Location> locations) {
        return new Door(
                (String) json.get("name"),
                (String) json.get("description"),
                locations.get(json.get("location1")),
                locations.get(json.get("location2")),
                Direction.valueOf((String) json.get("direction")),
                (String) json.get("descriptionTo2"),
                (String) json.get("descriptionTo1"),
                (Boolean) json.get("open"));
    }

    private Item parseItem(JSONObject json) {
        return new Item(
                (String) json.get("name"),
                (String) json.get("description"));
    }

    private Equipment parseEquipment(JSONObject json) {
        try {
            return new Equipment(
                    (String) json.get("name"),
                    (String) json.get("description"),
                    Slot.valueOf((String) json.get("slot")),
                    ((Long) json.get("defense")).intValue(),
                    parseSkillRequirements((JSONArray) json.getOrDefault("skillRequirements", new JSONArray())));
        } catch (Exception e) {
            throw new RuntimeException("Unable to parse equipment: "+ json.toJSONString(), e);
        }
    }

    private Weapon parseWeapon(JSONObject json) {
        return new Weapon(
                (String) json.get("name"),
                (String) json.get("description"),
                Slot.valueOf((String) json.get("slot")),
                parseSkillRequirements((JSONArray) json.getOrDefault("skillRequirements", new JSONArray())),
                ((Long) json.get("damageMin")).intValue(),
                ((Long) json.get("damageMax")).intValue(),
                Skill.valueOf((String) json.get("skill")));
    }

    private List<SkillRequirement> parseSkillRequirements(JSONArray json) {
        ArrayList<SkillRequirement> skillRequirements = new ArrayList<>();
        json.forEach(obj -> {
            JSONObject jsonObj = (JSONObject) obj;
            skillRequirements.add(new SkillRequirement(
                    Skill.valueOf((String) jsonObj.get("skill")),
                    ((Long) jsonObj.get("level")).intValue()));
        });
        return skillRequirements;
    }

    private void registerCommands() {
        commands = new HashMap<>();
        commands.put("MOVE", new MoveCommand());
        commands.put("CLIMB", new ClimbCommand());
        commands.put("OPEN", new OpenCommand());
        commands.put("QUIT", new QuitCommand());
        commands.put("DESCRIBE", new DescribeCommand());
        commands.put("UNLOCK", new UnlockCommand());
        commands.put("ATTACK", new AttackCommand());
        commands.put("KILL", new KillCommand());
        commands.put("TALK", new TalkCommand());
        commands.put("TAKE", new TakeCommand());
        commands.put("DROP", new DropCommand());
        commands.put("EQUIP", new EquipCommand());
        commands.put("LOOT", new LootCommand());
        commands.put("INVENTORY", new InventoryCommand());
        commands.put("EQUIPMENT", new EquipmentCommand());
        commands.put("STATS", new StatsCommand());
        commands.put("SKILLS", new SkillsCommand());
        commands.put("SHOP", new ShopCommand());
        commands.put("BUY", new BuyCommand());
    }

    public void run() {
        try {
            new DescribeCommand().perform("");
            while (!quit && player.isAlive()) {
                processLine(readLine());
            }
        } catch (IOException e) {
            System.err.println("Lost input stream");
        }
    }

    private String readLine() throws IOException {
        return bufferedReader.readLine();
    }

    private void printLine(String message) {
        printStream.println(message);
    }

    void processLine(String line) {
        String[] commandData = line.split(" ", 2);
        String commandText = commandData[0];
        String arguments = commandData.length > 1 ? commandData[1] : "";

        Command command = commands.get(commandText.toUpperCase());
        if (command != null) {
            command.perform(arguments);
        } else {
            unknownCommand.perform(line);
        }
    }

    public Player getPlayer() {
        return player;
    }

    public Location getLocation() {
        return location;
    }
}