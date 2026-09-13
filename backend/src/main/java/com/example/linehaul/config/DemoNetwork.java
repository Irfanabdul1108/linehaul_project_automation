package com.example.linehaul.config;

import java.util.List;

/**
 * The demo linehaul network that is loaded into an empty database.
 *
 * <p>Four depots, each with its own lanes, fleet, crew and freight - deliberately different from one
 * another, and written as compact tables so a reader can check the numbers at a glance. The rows are
 * chosen so that every case the smart assignment engine handles appears at least once:</p>
 *
 * <table border="1">
 *   <caption>Which row demonstrates what</caption>
 *   <tr><td>case A - exact lane match</td><td>LH-5001 onto LH-1029 (Bengaluru to Hyderabad)</td></tr>
 *   <tr><td>case B - route ends in the right city, different origin</td><td>LH-5001 onto LH-2022</td></tr>
 *   <tr><td>case C - destination is an intermediate stop</td><td>LH-5001 onto LH-1033 (Warangal, Hyderabad, Vizag)</td></tr>
 *   <tr><td>case D - cross-warehouse route with a valid stop here</td><td>LH-2022 and LH-3034 pass Bengaluru</td></tr>
 *   <tr><td>case E - right route, not enough capacity</td><td>LH-5002 to Chennai (LH-1030 is at 7,500 of 8,000 kg)</td></tr>
 *   <tr><td>case F - no matching route at all</td><td>LH-5005 to Kolkata, LH-6004 to Coimbatore (route is in transit)</td></tr>
 *   <tr><td>case G - new route must be created</td><td>LH-5002, LH-5005, LH-5008, LH-8003, LH-8006</td></tr>
 *   <tr><td>case H - local route still needs a driver and a truck</td><td>LH-1032 and LH-1036 (DRAFT, no crew)</td></tr>
 *   <tr><td>case I - cross-warehouse route already has driver and truck</td><td>LH-2022 (truck T-204, driver D-203)</td></tr>
 *   <tr><td>case J - no suitable driver or truck left in the depot</td><td>LH-5006 (9,500 kg), LH-7004, LH-7006</td></tr>
 *   <tr><td>a route that is closed for changes</td><td>LH-1035 in transit, LH-3035 dispatched, LH-2024 in transit</td></tr>
 *   <tr><td>a route that reaches the city but never comes to you</td><td>LH-4042 passes Chennai only, LH-1030 never reaches Kolkata</td></tr>
 * </table>
 *
 * <p>Table formats (pipe separated, loaded by {@link DataSeeder}):</p>
 * <pre>
 * trucks:    id|type|capacity kg|status|warehouse
 * drivers:   id|name|status|warehouse
 * routes:    id|origin|destination|stops (comma separated, may be empty)|departure|hours|capacity kg|truck|driver|warehouse
 * orders:    id|customer|origin|destination|weight kg|pieces|service date|status|route (empty = unassigned)|warehouse
 * </pre>
 */
final class DemoNetwork {

    private DemoNetwork() {
    }

    /**
     * id|designation|city|state|description
     *
     * <p>The <b>designation</b> ("Warehouse A") exists for the switching interface only - the place
     * the rest of the application shows, and the token every lane is written with, is the
     * <b>city</b>. {@link DataSeeder} therefore stores the city as the warehouse's hub location.</p>
     */
    static final List<String> WAREHOUSES = List.of(
            "WH-A|Warehouse A|Bengaluru|Karnataka|South consolidation centre for the Bengaluru metro lanes.",
            "WH-B|Warehouse B|Chennai|Tamil Nadu|Coastal depot; runs the Chennai - Hyderabad milk run over Bengaluru.",
            "WH-C|Warehouse C|Pune|Maharashtra|Western depot; the only one that cross-docks through Bengaluru on the way to Hyderabad.",
            "WH-D|Warehouse D|Kolkata|West Bengal|Eastern depot with long north-east lanes and little spare capacity."
    );

    /**
     * Reference travel times, {@code origin|destination|hours}.
     *
     * <p>The point of this table is that a dispatcher creating a route never has to know how long a
     * lane takes: the duration is remembered per city pair and filled in automatically. A lane has no
     * direction here - Bengaluru to Hyderabad is the same drive as Hyderabad to Bengaluru - and every
     * route that is created or edited afterwards keeps teaching the same table (see
     * {@code LaneDurationService}), so these numbers are only the starting point.</p>
     */
    static final List<String> LANE_DURATIONS = List.of(
            // out of Bengaluru (WH-A)
            "Bengaluru|Hyderabad|9",
            "Bengaluru|Chennai|8",
            "Bengaluru|Vijayawada|6",
            "Bengaluru|Vizag|15",
            "Bengaluru|Mumbai|16",
            "Bengaluru|Pune|12",
            "Bengaluru|Mysuru|3",
            "Bengaluru|Warangal|11",
            "Bengaluru|Kolkata|30",
            "Bengaluru|Nagpur|18",
            "Bengaluru|Coimbatore|7",
            // out of Chennai (WH-B)
            "Chennai|Hyderabad|14",
            "Chennai|Madurai|6",
            "Chennai|Coimbatore|9",
            "Chennai|Mysuru|9",
            "Chennai|Nellore|4",
            "Chennai|Vijayawada|8",
            "Chennai|Kolkata|24",
            // out of Pune (WH-C)
            "Pune|Mumbai|4",
            "Pune|Nagpur|8",
            "Pune|Ahmedabad|9",
            "Pune|Hyderabad|11",
            "Pune|Delhi|22",
            "Pune|Goa|8",
            // out of Kolkata (WH-D)
            "Kolkata|Guwahati|14",
            "Kolkata|Patna|7",
            "Kolkata|Bhubaneswar|9",
            "Kolkata|Hyderabad|26",
            "Kolkata|Ranchi|8",
            "Kolkata|Delhi|26",
            // lanes between the depots themselves
            "Bengaluru|Kolkata|30",
            "Chennai|Pune|18",
            "Pune|Kolkata|28",
            // other pairs the demo freight uses
            "Hyderabad|Vizag|9",
            "Hyderabad|Vijayawada|5",
            "Hyderabad|Nagpur|9",
            "Nagpur|Delhi|16",
            "Patna|Bhubaneswar|12",
            "Madurai|Coimbatore|4"
    );

    static final List<String> TRUCKS = List.of(
            // WH-A, Bengaluru
            "T-101|Truck|10000|ASSIGNED|WH-A",
            "T-102|Truck|8000|ASSIGNED|WH-A",
            "T-103|Van|4000|AVAILABLE|WH-A",
            "T-104|Tractor|12000|MAINTENANCE|WH-A",
            "T-105|Truck|9000|ASSIGNED|WH-A",
            "T-106|Truck|7000|IN_TRANSIT|WH-A",
            // WH-B, Chennai
            "T-201|Truck|9000|ASSIGNED|WH-B",
            "T-202|Tractor|14000|AVAILABLE|WH-B",
            "T-203|Van|3500|ASSIGNED|WH-B",
            "T-204|Truck|12000|ASSIGNED|WH-B",
            "T-205|Truck|6000|IN_TRANSIT|WH-B",
            "T-206|Trailer|18000|AVAILABLE|WH-B",
            // WH-C, Pune
            "T-301|Truck|11000|IN_TRANSIT|WH-C",
            "T-302|Truck|5000|ASSIGNED|WH-C",
            "T-303|Van|3000|MAINTENANCE|WH-C",
            "T-304|Tractor|13000|ASSIGNED|WH-C",
            // WH-D, Kolkata
            "T-401|Truck|10000|ASSIGNED|WH-D",
            "T-402|Van|3000|ASSIGNED|WH-D",
            "T-403|Tractor|15000|ASSIGNED|WH-D",
            "T-404|Truck|8000|AVAILABLE|WH-D"
    );

    static final List<String> DRIVERS = List.of(
            "D-101|John Smith|ASSIGNED|WH-A",
            "D-102|Priya Nair|ASSIGNED|WH-A",
            "D-103|Rahul Verma|ASSIGNED|WH-A",
            "D-104|Sameer Khan|AVAILABLE|WH-A",
            "D-105|Anita Rao|AVAILABLE|WH-A",
            "D-106|Vikram Reddy|IN_TRANSIT|WH-A",
            "D-201|Imran Sheikh|ASSIGNED|WH-B",
            "D-202|Lakshmi Menon|AVAILABLE|WH-B",
            "D-203|Suresh Babu|ASSIGNED|WH-B",
            "D-204|Farhan Ali|IN_TRANSIT|WH-B",
            "D-205|Nisha Ravi|AVAILABLE|WH-B",
            "D-301|Ravi Kulkarni|ASSIGNED|WH-C",
            "D-302|Sneha Patil|ASSIGNED|WH-C",
            "D-303|Amit Jadhav|AVAILABLE|WH-C",
            "D-304|Pooja Deshmukh|IN_TRANSIT|WH-C",
            "D-401|Arup Bose|ASSIGNED|WH-D",
            "D-402|Mita Ghosh|AVAILABLE|WH-D",
            "D-403|Tapan Dutta|ASSIGNED|WH-D",
            "D-404|Bulu Sarkar|AVAILABLE|WH-D"
    );

    /**
     * id|origin|destination|stops|departure|hours|capacity|truck|driver|warehouse[|status]
     *
     * <p>A row without a status gets one derived from its own data: no crew at all is a DRAFT, a
     * complete route is READY, half-built is BLOCKED. Only the three lanes that already left are
     * spelled out (IN_TRANSIT / DISPATCHED), so the engine can be seen refusing a closed route.
     *
     * <p>Lanes are written with the <b>city</b> of a depot, never with its "Warehouse X" designation:
     * that name belongs to the switching interface alone. Note that the crew of a cross-warehouse
     * route belongs to the depot that owns the route: LH-2022 is a Chennai truck that stops at
     * Bengaluru, so a Bengaluru order can be added to it without touching its driver or truck.</p>
     */
    static final List<String> ROUTES = List.of(
            // WH-A, Bengaluru
            "LH-1029|Bengaluru|Hyderabad|Vijayawada|20:00|9|10000|T-101|D-103|WH-A",
            "LH-1030|Bengaluru|Chennai||21:30|8|8000|T-102|D-101|WH-A",
            "LH-1032|Bengaluru|Vijayawada||06:00|6|5000|||WH-A",
            "LH-1033|Bengaluru|Vizag|Warangal,Hyderabad|19:00|15|9000|T-105|D-102|WH-A",
            "LH-1035|Bengaluru|Mumbai||05:00|16|7000|T-106|D-106|WH-A|IN_TRANSIT",
            "LH-1036|Bengaluru|Pune||23:00|12|6000|||WH-A",
            // WH-B, Chennai
            "LH-2021|Chennai|Bengaluru||22:00|10|7000|T-201|D-201|WH-B",
            "LH-2022|Chennai|Hyderabad|Bengaluru,Vijayawada|18:00|14|12000|T-204|D-203|WH-B",
            "LH-2023|Chennai|Madurai||07:00|6|4000|T-203||WH-B",
            "LH-2024|Chennai|Coimbatore|Madurai|06:30|9|6000|T-205|D-204|WH-B|IN_TRANSIT",
            "LH-2025|Chennai|Mysuru|Bengaluru|09:00|9|8000|||WH-B",
            // WH-C, Pune
            "LH-3031|Pune|Mumbai||05:30|4|11000|T-304|D-301|WH-C",
            "LH-3032|Pune|Nagpur||08:00|8|5000|||WH-C",
            "LH-3033|Pune|Ahmedabad||06:00|9|6000|T-302|D-302|WH-C",
            "LH-3034|Pune|Hyderabad|Bengaluru|17:00|11|9000|||WH-C",
            "LH-3035|Pune|Delhi|Nagpur|04:00|22|13000|T-301|D-304|WH-C|DISPATCHED",
            // WH-D, Kolkata
            "LH-4041|Kolkata|Guwahati||05:00|14|10000|T-401|D-401|WH-D",
            "LH-4042|Kolkata|Hyderabad|Chennai|10:00|26|15000|T-403|D-403|WH-D|IN_TRANSIT",
            "LH-4043|Kolkata|Patna||07:30|7|6000|||WH-D",
            "LH-4044|Kolkata|Bhubaneswar|Patna|06:00|9|4000|T-402||WH-D"
    );

    /**
     * id|customer|origin|destination|weight|pieces|service date|status|route|warehouse
     *
     * <p>Rows without a route id are the unassigned pile the feature works on.</p>
     */
    static final List<String> ORDERS = List.of(
            // ---- WH-A (Bengaluru): unassigned pile with one of every case
            "LH-5001|Coastal Traders|Bengaluru|Hyderabad|1500|12|2026-09-15|READY||WH-A",
            "LH-5002|Deccan Foods|Bengaluru|Chennai|900|6|2026-09-15|READY||WH-A",
            "LH-5003|Vijaya Textiles|Bengaluru|Vijayawada|800|10|2026-09-16|READY||WH-A",
            "LH-5004|Sai Hardware|Bengaluru|Pune|1200|5|2026-09-16|READY||WH-A",
            "LH-5005|Krishna Steel|Bengaluru|Kolkata|2200|14|2026-09-17|READY||WH-A",
            "LH-5006|Parimal Bulk|Bengaluru|Hyderabad|9500|40|2026-09-17|READY||WH-A",
            "LH-5007|Trendz Apparel|Bengaluru|Hyderabad|600|4|2026-09-18|CREATED||WH-A",
            "LH-5008|Metro Haulers|Bengaluru|Nagpur|1400|8|2026-09-18|READY||WH-A",
            "LH-5009|Sunrise Cargo|Bengaluru|Chennai|300|3|2026-09-19|BLOCKED||WH-A",
            "LH-5010|Trendset Retail|Bengaluru|Vizag|1500|9|2026-09-19|READY||WH-A",
            // ---- WH-A (Bengaluru): already on a route
            "LH-5011|ABC Logistics|Bengaluru|Hyderabad|2000|8|2026-09-14|ASSIGNED|LH-1029|WH-A",
            "LH-5012|Sunrise Cargo|Bengaluru|Hyderabad|2200|11|2026-09-14|ASSIGNED|LH-1029|WH-A",
            "LH-5013|Metro Haulers|Bengaluru|Hyderabad|2000|9|2026-09-14|ASSIGNED|LH-1029|WH-A",
            "LH-5014|Delta Shipping|Bengaluru|Chennai|2600|12|2026-09-14|ASSIGNED|LH-1030|WH-A",
            "LH-5015|Fast Freight Co|Bengaluru|Chennai|2500|10|2026-09-14|ASSIGNED|LH-1030|WH-A",
            "LH-5016|Peak Distribution|Bengaluru|Chennai|2400|7|2026-09-14|ASSIGNED|LH-1030|WH-A",
            "LH-5017|Initech Freight|Bengaluru|Vizag|4000|15|2026-09-13|ASSIGNED|LH-1033|WH-A",
            "LH-5018|Blue Line Transport|Bengaluru|Mumbai|3000|12|2026-09-12|IN_TRANSIT|LH-1035|WH-A",
            "LH-5019|Nova Retail|Bengaluru|Pune|1000|4|2026-09-15|ASSIGNED|LH-1036|WH-A",

            // ---- WH-B (Chennai)
            "LH-6001|Anbu Traders|Chennai|Hyderabad|1800|9|2026-09-15|READY||WH-B",
            "LH-6002|Chennai Exports|Chennai|Bengaluru|1300|7|2026-09-15|READY||WH-B",
            "LH-6003|Madurai Metals|Chennai|Madurai|3600|11|2026-09-16|READY||WH-B",
            "LH-6004|Port City Freight|Chennai|Coimbatore|2200|8|2026-09-16|READY||WH-B",
            "LH-6005|Thanjavur Agro|Chennai|Madurai|900|4|2026-09-17|READY||WH-B",
            "LH-6007|Kaveri Polymers|Chennai|Nagpur|2600|12|2026-09-17|MANIFESTED||WH-B",
            "LH-6011|Gulf Packaging|Chennai|Bengaluru|3000|10|2026-09-14|ASSIGNED|LH-2021|WH-B",
            "LH-6012|Coromandel Goods|Chennai|Bengaluru|2000|6|2026-09-14|ASSIGNED|LH-2021|WH-B",
            "LH-6013|Sri Balaji Mills|Chennai|Hyderabad|3000|13|2026-09-14|ASSIGNED|LH-2022|WH-B",
            "LH-6014|Vellore Castings|Chennai|Hyderabad|2000|8|2026-09-14|IN_TRANSIT|LH-2022|WH-B",
            "LH-6015|Coimbatore Lathe|Chennai|Coimbatore|2000|9|2026-09-13|DISPATCHED|LH-2024|WH-B",
            "LH-6016|Tirupur Knits|Chennai|Coimbatore|1800|7|2026-09-13|DISPATCHED|LH-2024|WH-B",
            "LH-6017|Nellore Stone|Chennai|Nellore|1500|5|2026-09-10|COMPLETED||WH-B",

            // ---- WH-C (Pune)
            "LH-7001|Deccan Traders|Pune|Hyderabad|2400|9|2026-09-15|READY||WH-C",
            "LH-7002|Pune Fabrics|Pune|Mumbai|900|3|2026-09-15|READY||WH-C",
            "LH-7003|Bhosale Imports|Pune|Nagpur|3200|12|2026-09-16|READY||WH-C",
            "LH-7004|Kalyani Steel|Pune|Goa|2100|7|2026-09-16|READY||WH-C",
            "LH-7005|Nashik Agro|Pune|Ahmedabad|1500|6|2026-09-17|READY||WH-C",
            "LH-7006|Satara Tools|Pune|Delhi|4800|16|2026-09-17|READY||WH-C",
            "LH-7011|Chakan Motors|Pune|Mumbai|2500|10|2026-09-14|ASSIGNED|LH-3031|WH-C",
            "LH-7012|Akurdi Plastics|Pune|Mumbai|3100|11|2026-09-14|ASSIGNED|LH-3031|WH-C",
            "LH-7013|Hadapsar Retail|Pune|Ahmedabad|1800|8|2026-09-14|ASSIGNED|LH-3033|WH-C",
            "LH-7014|Wakad Electronics|Pune|Delhi|6000|18|2026-09-12|IN_TRANSIT|LH-3035|WH-C",
            "LH-7015|Pimpri Hardware|Pune|Delhi|4000|14|2026-09-12|IN_TRANSIT|LH-3035|WH-C",

            // ---- WH-D (Kolkata)
            "LH-8001|Kolkata Spices|Kolkata|Guwahati|1200|5|2026-09-15|READY||WH-D",
            "LH-8002|Ganges Polymers|Kolkata|Patna|2600|9|2026-09-15|READY||WH-D",
            "LH-8003|Howrah Engineering|Kolkata|Chennai|5200|15|2026-09-16|READY||WH-D",
            "LH-8004|Barasat Foods|Kolkata|Ranchi|800|3|2026-09-16|READY||WH-D",
            "LH-8005|Ultadanga Trades|Kolkata|Bhubaneswar|1500|4|2026-09-17|READY||WH-D",
            "LH-8006|Sealdah Cargo|Kolkata|Guwahati|3000|11|2026-09-17|READY||WH-D",
            "LH-8011|Siliguri Tea|Kolkata|Guwahati|4200|13|2026-09-13|ASSIGNED|LH-4041|WH-D",
            "LH-8012|Dankuni Steel|Kolkata|Guwahati|3300|12|2026-09-13|ASSIGNED|LH-4041|WH-D",
            "LH-8013|Salt Lake Imports|Kolkata|Hyderabad|5000|16|2026-09-11|IN_TRANSIT|LH-4042|WH-D"
    );
}
