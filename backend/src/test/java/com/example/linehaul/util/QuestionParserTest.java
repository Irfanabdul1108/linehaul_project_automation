package com.example.linehaul.util;

import com.example.linehaul.util.ParsedQuestion.Action;
import com.example.linehaul.util.ParsedQuestion.Entity;
import com.example.linehaul.util.ParsedQuestion.Focus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuestionParserTest {

    @Test
    void readsEntityAndAction() {
        ParsedQuestion routes = QuestionParser.parse("How many routes are there?");
        assertEquals(Entity.ROUTE, routes.entity());
        assertEquals(Action.COUNT, routes.action());
        assertNull(routes.status());

        ParsedQuestion drivers = QuestionParser.parse("Show available drivers.");
        assertEquals(Entity.DRIVER, drivers.entity());
        assertEquals(Action.LIST, drivers.action());
        assertEquals("AVAILABLE", drivers.status());
    }

    @Test
    void readsSynonyms() {
        ParsedQuestion trucks = QuestionParser.parse("How many trucks are free?");
        assertEquals(Entity.VEHICLE, trucks.entity());
        assertEquals(Action.COUNT, trucks.action());
        assertEquals("AVAILABLE", trucks.status());

        ParsedQuestion shipments = QuestionParser.parse("List all shipments");
        assertEquals(Entity.ORDER, shipments.entity());
        assertEquals(Action.LIST, shipments.action());
    }

    @Test
    void readsStatuses() {
        assertEquals("READY", QuestionParser.parse("How many READY orders are there?").status());
        assertEquals("BLOCKED", QuestionParser.parse("Show blocked routes.").status());
        assertEquals("MAINTENANCE", QuestionParser.parse("Which vehicles are in maintenance?").status());
        assertEquals("DISPATCHED", QuestionParser.parse("How many dispatched routes?").status());
        assertEquals("UNASSIGNED", QuestionParser.parse("Which orders are not assigned?").status());
        assertEquals("ASSIGNED", QuestionParser.parse("Show assigned drivers").status());
    }

    @Test
    void readsIds() {
        assertEquals("LH-1029", QuestionParser.parse("What is the status of LH-1029?").id());
        assertEquals("LH-1029", QuestionParser.parse("what is the eta of lh1029").id());
        assertEquals("D-101", QuestionParser.parse("Tell me about D-101").id());
        assertEquals("T-182", QuestionParser.parse("Where is truck T-182?").id());
        assertNull(QuestionParser.parse("How many routes are there?").id());
    }

    @Test
    void readsFocusForOneId() {
        assertEquals(Focus.ETA, QuestionParser.parse("What is the ETA of LH-1029?").focus());
        assertEquals(Focus.CAPACITY, QuestionParser.parse("What is the capacity of LH-1029?").focus());
        assertEquals(Focus.DRIVER, QuestionParser.parse("Who is assigned to LH-1029?").focus());
        assertEquals(Focus.TRUCK, QuestionParser.parse("Which truck is assigned to LH-1029?").focus());
        assertEquals(Focus.ORDERS, QuestionParser.parse("How many orders are on LH-1029?").focus());
        assertEquals(Focus.NONE, QuestionParser.parse("What is the status of LH-1029?").focus());
    }

    @Test
    void readsFocusForGroups() {
        assertEquals(Focus.MOST_ORDERS, QuestionParser.parse("Which route has the most orders?").focus());
        assertEquals(Focus.HIGHEST_CAPACITY, QuestionParser.parse("Which route has the highest capacity?").focus());
        assertEquals(Focus.NO_DRIVER, QuestionParser.parse("Which routes don't have a driver?").focus());
        assertEquals(Focus.NO_TRUCK, QuestionParser.parse("Which routes do not have a truck?").focus());
        assertEquals(Focus.NO_ORDERS, QuestionParser.parse("Which routes have no orders?").focus());
        assertEquals(Focus.ORDERS_PER_ROUTE,
                QuestionParser.parse("How many orders are assigned to each route?").focus());
    }

    @Test
    void readsGreetingAndHelp() {
        assertEquals(Focus.GREETING, QuestionParser.parse("Hello").focus());
        assertEquals(Focus.GREETING, QuestionParser.parse("hi there").focus());
        assertEquals(Focus.HELP, QuestionParser.parse("help").focus());
        assertEquals(Focus.HELP, QuestionParser.parse("what can you do?").focus());
    }

    @Test
    void keepsRouteNegationOffOtherEntities() {
        ParsedQuestion drivers = QuestionParser.parse("Which drivers are not assigned?");
        assertEquals(Entity.DRIVER, drivers.entity());
        assertEquals(Focus.NONE, drivers.focus());
        assertEquals("UNASSIGNED", drivers.status());
    }

    @Test
    void normalizesText() {
        assertEquals("how many routes", QuestionParser.normalize("  How many ROUTES?!  "));
        assertEquals("", QuestionParser.normalize(null));
    }
}
