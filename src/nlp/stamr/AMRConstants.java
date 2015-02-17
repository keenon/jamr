package nlp.stamr;

import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Quadruple;

import java.util.*;

/**
 * A bunch of static lists and facts about edu.stanford.nlp.stamr.AMR and its various node types, reifications, and other kinds of lists.
 *
 * Painstakingly copied (with the help of many-a-macro) from https://github.com/amrisi/amr-guidelines/blob/master/amr.md
 */

public class AMRConstants {

    public static Map<String,Pair<String,String>> commonNamedEntityConfusions = new HashMap<String, Pair<String, String>>();
    static {
        commonNamedEntityConfusions.put("canadian", new Pair<String, String>("country", "Canada"));
        commonNamedEntityConfusions.put("canada", new Pair<String, String>("country", "Canada"));
        commonNamedEntityConfusions.put("afghani", new Pair<String, String>("country", "Afghanistan"));
        commonNamedEntityConfusions.put("afghanistan", new Pair<String, String>("country", "Afghanistan"));
        commonNamedEntityConfusions.put("polish", new Pair<String, String>("country", "Poland"));
        commonNamedEntityConfusions.put("poland", new Pair<String, String>("country", "Poland"));
        commonNamedEntityConfusions.put("china", new Pair<String, String>("country", "China"));
        commonNamedEntityConfusions.put("chinese", new Pair<String, String>("country", "China"));
        commonNamedEntityConfusions.put("japan", new Pair<String, String>("country", "Japan"));
        commonNamedEntityConfusions.put("japanese", new Pair<String, String>("country", "Japan"));
        commonNamedEntityConfusions.put("korea", new Pair<String, String>("country", "Korea"));
        commonNamedEntityConfusions.put("korean", new Pair<String, String>("country", "Korea"));
        commonNamedEntityConfusions.put("american", new Pair<String, String>("country", "United States"));
        commonNamedEntityConfusions.put("america", new Pair<String, String>("country", "United States"));
        commonNamedEntityConfusions.put("u.s.", new Pair<String, String>("country", "United States"));
        commonNamedEntityConfusions.put("russia", new Pair<String, String>("country", "Russia"));
        commonNamedEntityConfusions.put("russian", new Pair<String, String>("country", "Russia"));
        commonNamedEntityConfusions.put("iraq", new Pair<String, String>("country", "Iraq"));
        commonNamedEntityConfusions.put("iraqi", new Pair<String, String>("country", "Iraqi"));
    }

    @SuppressWarnings("unchecked")
    public static Quadruple<String,String,String,String>[] reificationData = new Quadruple[]{
            Quadruple.makeQuadruple("accompanier", "accompany-01", "ARG0", "ARG1"),
            Quadruple.makeQuadruple("age", "age-01", "ARG1", "ARG2"),
            Quadruple.makeQuadruple("beneficiary", "benefit-01", "ARG0", "ARG1"),
            Quadruple.makeQuadruple("cause", "cause-01", "ARG1", "ARG0"),
            Quadruple.makeQuadruple("concession", "have-concession-91", "ARG1", "ARG2"),
            Quadruple.makeQuadruple("condition", "have-condition-91", "ARG1", "ARG2"),
            Quadruple.makeQuadruple("destination", "be-destined-for-91", "ARG1", "ARG2"),
            Quadruple.makeQuadruple("duration", "last-01", "ARG1", "ARG2"),
            Quadruple.makeQuadruple("example", "exemplify-01", "ARG0", "ARG1"),
            Quadruple.makeQuadruple("frequency", "have-frequency-91", "ARG1", "ARG2"),
            Quadruple.makeQuadruple("instrument", "have-instrument-91", "ARG1", "ARG2"),
            Quadruple.makeQuadruple("location", "be-located-at-91", "ARG1", "ARG2"),
            Quadruple.makeQuadruple("manner", "have-manner-91", "ARG1", "ARG2"),
            Quadruple.makeQuadruple("mod", "have-mod-91", "ARG1", "ARG2"),
            Quadruple.makeQuadruple("part", "have-part-91", "ARG1", "ARG2"),
            Quadruple.makeQuadruple("polarity", "have-polarity-91", "ARG1", "ARG2"),
            Quadruple.makeQuadruple("poss", "own-01, have-03", "ARG0", "ARG1"),
            Quadruple.makeQuadruple("purpose", "have-purpose-91", "ARG1", "ARG2"),
            Quadruple.makeQuadruple("quant", "have-quant-91", "ARG1", "ARG2"),
            Quadruple.makeQuadruple("source", "be-from-91", "ARG1", "ARG2"),
            Quadruple.makeQuadruple("subevent", "have-subevent-91", "ARG1", "ARG2"),
            Quadruple.makeQuadruple("subset", "include-91", "ARG2", "ARG1"),
            Quadruple.makeQuadruple("time", "be-temporally-at-91", "ARG1", "ARG2"),
            Quadruple.makeQuadruple("topic", "concern-02", "ARG0", "ARG1")
    };
    public static Map<String,String> relationToReification = new HashMap<String, String>();
    public static Map<String,String> relationToParentArc = new HashMap<String, String>();
    public static Map<String,String> relationToChildArc = new HashMap<String, String>();
    public static Map<String,String> reificationToRelation = new HashMap<String, String>();
    public static Map<String,String> reificationToParentArc = new HashMap<String, String>();
    public static Map<String,String> reificationToChildArc = new HashMap<String, String>();

    static {
        for (Quadruple<String,String,String,String> reificationQuad : reificationData) {
            String relation = reificationQuad.first;
            String reification = reificationQuad.second;
            String parentArc = reificationQuad.third+"-of";
            String childArc = reificationQuad.fourth;
            relationToReification.put(relation,reification);
            relationToParentArc.put(relation,parentArc);
            relationToChildArc.put(relation,childArc);
            reificationToRelation.put(reification,relation);
            reificationToParentArc.put(reification,parentArc);
            reificationToChildArc.put(reification,childArc);
        }
    }

    public static Map<Integer,String> weekdays = new HashMap<Integer, String>();

    static {
        weekdays.put(1, "Monday");
        weekdays.put(2, "Tuesday");
        weekdays.put(3, "Wednesday");
        weekdays.put(4, "Thursday");
        weekdays.put(5, "Friday");
        weekdays.put(6, "Saturday");
        weekdays.put(7, "Sunday");
    }

    public static String cleanPreposition(String prep) {
        if (prep.startsWith("ObjectOfVerb")) return "domain";
        if (prep.startsWith("Temporal")) return "time";
        if (prep.startsWith("PhysicalSupport")) return "location";
        if (prep.startsWith("Recipient")) return "domain";
        for (String rel : relationToReification.keySet()) {
            if (prep.toLowerCase().startsWith(rel)) return rel;
        }
        return prep.split("-")[0].toLowerCase().replaceAll("/","-");
    }

    public static String getEntityType(String name) {
        name = name.toLowerCase();

        if (name.equals("person")) return "PERSON";
        if (nerTaxonomy.contains(name)) return "THING";
        if (quantityTaxonomy.contains(name)) return "QUANT";
        if (name.equals("date-entity")) return "DATE";
        if (monthToOffset.containsKey(name)) return "MONTH";
        if (conjunctions.contains(name)) return "CONJUNCTION";
        if (pronouns.contains(name)) return "PRONOUN";
        if (modals.contains(name)) return "MODAL";

        if (name.contains("-")) {
            String[] parts = name.split("-");
            if (parts.length == 2) {
                try {
                    Integer.parseInt(parts[1]);
                    return "VERB";
                }
                catch (Exception ignored) {}
            }
        }

        return "NOUN";
    }

    public static Set<String> nerTaxonomy = new HashSet<String>(
        Arrays.asList(new String[] {
            "thing",

            "person",
             "family",
             "animal",
             "language",
             "nationality",
             "ethnic-group",
             "regional-group",
             "religious-group",

            "organization",
             "company",
             "government-organization",
             "military",
             "criminal-organization",
             "political-party",
             "school",
             "university",
             "research-institute",
             "team",
             "league",

            "location",
             "city",
             "city-district",
             "county",
             "local-region",
             "state",
             "province",
             "country",
             "country-region",
             "world-region",
             "continent",
             "ocean",
             "sea",
             "lake",
             "river",
             "gulf",
             "bay",
             "strait",
             "canal",
             "peninsula",
             "mountain",
             "volcano",
             "valley",
             "canyon",
             "island",
             "desert",
             "forest",
             "moon",
             "planet",
             "star",
             "constellation",

            "facility",
             "airport",
             "station",
             "port",
             "tunnel",
             "bridge",
             "road",
             "railway-line",
             "canal",
             "building",
             "theater",
             "museum",
             "palace",
             "hotel",
             "worship-place",
             "market",
             "sports-facility",
             "park",
             "zoo",
             "amusement-park",

            "event",
             "incident",
             "natural-disaster",
             "earthquake",
             "war",
             "conference",
             "game",
             "festival",

            "product",
             "vehicle",
             "ship",
             "aircraft",
             "aircraft-type",
             "spaceship",
             "car-make",
             "work-of-art",
             "picture",
             "music",
             "show",
             "broadcast-program",

            "publication",
             "book",
             "newspaper",
             "magazine",
             "journal",

            "natural-object",

            "law",
             "treaty",
             "award",
             "food-dish",
             "disease"
        })
    );

    public static Set<String> quantityTaxonomy = new HashSet<String>(
        Arrays.asList(new String[] {
            "monetary-quantity",
            "distance-quantity",
            "area-quantity",
            "volume-quantity",
            "temporal-quantity",
            "frequency-quantity",
            "speed-quantity",
            "acceleration-quantity",
            "mass-quantity",
            "force-quantity",
            "pressure-quantity",
            "energy-quantity",
            "power-quantity",
            "voltage-quantity",
            "charge-quantity",
            "potential-quantity",
            "resistance-quantity",
            "inductance-quantity",
            "magnetic-field-quantity",
            "magnetic-flux-quantity",
            "radiation-quantity",
            "concentration-quantity",
            "temperature-quantity",
            "score-quantity",
            "fuel-consumption-quantity",
            "seismic-quantity"
        })
    );

    public static Set<String> miscTaxnomy = new HashSet<String>(
        Arrays.asList(new String[] {
            "date-entity",
            "ordinal-entity",
            "date-interval",
            "percentage-entity",
            "phone-number-entity",
            "email-address-entity",
            "url-entity"
        })
    );

    public static Set<String> mergeable = new HashSet<String>(
            Arrays.asList(new String[] {
                    "i"
                    ,"we"
                    ,"them"
                    ,"they"
                    ,"us"
            })
    );

    public static Set<String> pronouns = new HashSet<String>(
            Arrays.asList(new String[] {
                    "it"
                    ,"those"
                    ,"them"
                    ,"they"
                    ,"their"
                    ,"theirs"
                    ,"our"
                    ,"ours"
                    ,"we"
                    ,"us"
            })
    );

    public static Set<String> conjunctions = new HashSet<String>(
            Arrays.asList(new String[] {
                    "and"
                    ,"or"
            })
    );

    public static Set<String> commonLightVerbs = new HashSet<String>(
            Arrays.asList(new String[] {
                    "took"
                    ,"take"
                    ,"got"
                    ,"made"
                    ,"let"
            })
    );

    public static Set<String> commonExistentials = new HashSet<String>(
            Arrays.asList(new String[] {
                    "be-01"
                    ,"be-01"
                    ,"have-03"
                    ,"had"
            })
    );

    public static Set<String> modals = new HashSet<String>(
            Arrays.asList(new String[] {
                    "possible"
                    ,"likely"
                    ,"obligate-01"
                    ,"permit-01"
                    ,"recommend-01"
                    ,"prefer-01"
            })
    );

    public static Set<String> discourse = new HashSet<String>(
            Arrays.asList(new String[] {
                    "contrast-01"
            })
    );

    public static Set<String> sentenceIndicators = new HashSet<String>(
            Arrays.asList(new String[] {
                    "."
                    ,";"
            })
    );

    public static Set<String> combinedTaxonomy;

    static {
        combinedTaxonomy = new HashSet<String>();
        combinedTaxonomy.addAll(nerTaxonomy);
        combinedTaxonomy.addAll(quantityTaxonomy);
        combinedTaxonomy.addAll(miscTaxnomy);
    }

    public static Map<String,Set<String>> commonAMRisms = new HashMap<String, Set<String>>();

    static {
        commonAMRisms.put("i", new HashSet<String>(
                Arrays.asList(new String[] {
                        "i",
                        "I",
                        "me",
                        "my",
                        "myself",
                        "mine"
                })
        ));
        commonAMRisms.put("and", new HashSet<String>(
                Arrays.asList(new String[] {
                        "and",
                        "nor",
                        ":",
                        ","
                })
        ));
        commonAMRisms.put("or", new HashSet<String>(
                Arrays.asList(new String[] {
                        "or",
                        "nor"
                })
        ));
        commonAMRisms.put("you", new HashSet<String>(
                Arrays.asList(new String[] {
                        "you"
                })
        ));
        commonAMRisms.put("interrogative", new HashSet<String>(
                Arrays.asList(new String[] {
                        "whether"
                        ,"?"
                        ,"where"
                        ,"who"
                        ,"what"
                        ,"when"
                        ,"why"
                })
        ));
        commonAMRisms.put("cause-01", new HashSet<String>(
                Arrays.asList(new String[] {
                        "why"
                        ,"since"
                        ,"as"
                        ,"so"
                })
        ));
        commonAMRisms.put("possible", new HashSet<String>(
                Arrays.asList(new String[] {
                        "able"
                        ,"could"
                        ,"can"
                        ,"ca"
                })
        ));
        commonAMRisms.put("have-concession-91", new HashSet<String>(
                Arrays.asList(
                        "but",
                        "however"
                )
        ));
        commonAMRisms.put("recommend-01", new HashSet<String>(
                Arrays.asList(new String[] {
                        "should"
                        ,"ought"
                })
        ));
        commonAMRisms.put("obligate-01", new HashSet<String>(
                Arrays.asList(new String[] {
                        "have"
                        ,"must"
                })
        ));
        commonAMRisms.put("contrast-01", new HashSet<String>(
                Arrays.asList(new String[] {
                        "but"
                        ,"however"
                        ,"while"
                })
        ));
        commonAMRisms.put("-", new HashSet<String>(
                Arrays.asList(new String[] {
                        "no"
                        ,"not"
                        ,"without"
                        ,"cannot"
                        ,"unable"
                        ,"never"
                        ,"n't"
                        ,"neither"
                })
        ));
        commonAMRisms.put("relative-position", new HashSet<String>(
                Arrays.asList(new String[] {
                        "from"
                        ,"to"
                })
        ));
        commonAMRisms.put("amr-unknown", new HashSet<String>(
                Arrays.asList(new String[] {
                        "what"
                        ,"why"
                        ,"?"
                })
        ));
        commonAMRisms.put("this", new HashSet<String>(
                Arrays.asList(new String[] {
                        "this"
                        ,"these"
                        ,"that"
                })
        ));
        commonAMRisms.put("multi-sentence", new HashSet<String>(
                Arrays.asList(new String[] {
                        "."
                        ,";"
                })
        ));
        commonAMRisms.put("monetary-quantity", new HashSet<String>(
                Arrays.asList(
                        "dollar",
                        "dollars"
                )
        ));
        commonAMRisms.put("seismic-quantity", new HashSet<String>(
                Arrays.asList(
                        "magnitude"
                )
        ));
        // Putting in an empty set forces lexical features to take a holiday
        commonAMRisms.put("name", new HashSet<String>(
                Arrays.asList(new String[] {
                })
        ));
        commonAMRisms.put("thing", new HashSet<String>(
                Arrays.asList(new String[] {
                })
        ));
    }

    public static Set<String> trimmedSuffixes = new HashSet<String>(
            Arrays.asList(new String[] {
                    "ly"
                    ,"ing"
                    ,"ings"
                    ,"ed"
                    ,"d"
                    ,"ese"
                    ,"ial"
                    ,"ials"
                    ,"ian"
                    ,"ians"
                    ,"y"
                    ,"e"
                    ,"ic"
                    ,"ees"
                    ,"s"
                    ,"-00"
                    ,"-01"
                    ,"-02"
                    ,"-03"
                    ,"-04"
                    ,"-05"
                    ,"-06"
                    ,"-07"
                    ,"-08"
                    ,"-09"
                    ,"-10"
                    ,"-91"
            })
    );

    public static Set<String> commonNominals = new HashSet<String>(
            Arrays.asList(new String[] {
                    "thing"
                    ,"person"
                    ,"have-org-role-91"
            })
    );

    public static Map<String,String> srlToAMR = new HashMap<String, String>();

    static {
        srlToAMR.put("A0","ARG0");
        srlToAMR.put("A1","ARG1");
        srlToAMR.put("C-A1","ARG1");
        srlToAMR.put("A2","ARG2");
        srlToAMR.put("A3","ARG3");
        srlToAMR.put("A4","ARG4");
        srlToAMR.put("A5","ARG5");
        srlToAMR.put("R-AM-MNR", "ARG0");
        srlToAMR.put("AM-TMP", "time");
        srlToAMR.put("AM-ADV", "mod");
        srlToAMR.put("AM-MOD", "mod");
        srlToAMR.put("AM-DIR", "direction");
        srlToAMR.put("AM-NEG", "polarity");
        srlToAMR.put("AM-MNR", "manner");
        srlToAMR.put("AM-LOC", "location");
        srlToAMR.put("AM-CAU", "cause");
    }

    public static Map<String,String[]> confounderToName = new HashMap<String, String[]>();

    static {
        confounderToName.put("american",new String[]{"United", "States"});
        confounderToName.put("u.s.",new String[]{"United", "States"});
    }

    public static Map<String,Integer> monthToOffset = new HashMap<String, Integer>();

    static {
        monthToOffset.put("january", 1);
        monthToOffset.put("jan", 1);
        monthToOffset.put("february", 2);
        monthToOffset.put("feb", 2);
        monthToOffset.put("march", 3);
        monthToOffset.put("mar", 3);
        monthToOffset.put("april", 4);
        monthToOffset.put("apr", 4);
        monthToOffset.put("may", 5);
        monthToOffset.put("june", 6);
        monthToOffset.put("jun", 6);
        monthToOffset.put("july", 7);
        monthToOffset.put("jul", 7);
        monthToOffset.put("august", 8);
        monthToOffset.put("aug", 8);
        monthToOffset.put("september", 9);
        monthToOffset.put("sep", 9);
        monthToOffset.put("october", 10);
        monthToOffset.put("oct", 10);
        monthToOffset.put("november", 11);
        monthToOffset.put("nov", 11);
        monthToOffset.put("december", 12);
        monthToOffset.put("dec", 12);
    }

    public static List<TokenSequencePattern> govPatterns = new ArrayList<TokenSequencePattern>(){{
        add(TokenSequencePattern.compile("[{ner:ORGANIZATION}]* [{lemma:/[Dd]efen[cs]e/}] [{ner:ORGANIZATION}]*"));
        add(TokenSequencePattern.compile("[{ner:ORGANIZATION}]* [{lemma:/[Ff]oreign/}] [{ner:ORGANIZATION}]*"));
        add(TokenSequencePattern.compile("[{ner:ORGANIZATION}]* [{lemma:/[Dd]efense/}] [{ner:ORGANIZATION}]*"));
        add(TokenSequencePattern.compile("[{ner:ORGANIZATION}]* [{lemma:/[Pp]arliament/}] [{ner:ORGANIZATION}]*"));
        add(TokenSequencePattern.compile("[{ner:ORGANIZATION}]* [{lemma:/[Cc]ongress/}] [{ner:ORGANIZATION}]*"));
        add(TokenSequencePattern.compile("[{ner:ORGANIZATION}]* [{lemma:/[Bb]ureau/}] [{ner:ORGANIZATION}]*"));
        add(TokenSequencePattern.compile("[{ner:ORGANIZATION}]* [{lemma:/[Pp]eople/}] [{lemma:/'s/}] [{ner:ORGANIZATION}]*"));
        add(TokenSequencePattern.compile("[{ner:ORGANIZATION}]* [{lemma:/[Oo]ffice/}] [{ner:ORGANIZATION}]*"));
        add(TokenSequencePattern.compile("[{ner:ORGANIZATION}]* [{lemma:/[Hh]ouse/}] [{ner:ORGANIZATION}]*"));
        add(TokenSequencePattern.compile("[{ner:ORGANIZATION}]* [{lemma:/[Ff]ederal/}] [{ner:ORGANIZATION}]*"));
        add(TokenSequencePattern.compile("[{ner:ORGANIZATION}]* [{lemma:/[Cc]ommission/}] [{ner:ORGANIZATION}]*"));
        add(TokenSequencePattern.compile("[{ner:ORGANIZATION}]* [{lemma:/[Nn]arcotics/}] [{ner:ORGANIZATION}]*"));
        add(TokenSequencePattern.compile("[{ner:ORGANIZATION}]* [{lemma:/[Mm]inistry/}] [{ner:ORGANIZATION}]*"));
        add(TokenSequencePattern.compile("[{ner:ORGANIZATION}]* [{lemma:/[Nn]ational/}] [{ner:ORGANIZATION}]*"));
        add(TokenSequencePattern.compile("[{ner:ORGANIZATION}]* [{lemma:/[Dd]epartment/}] [{ner:ORGANIZATION}]*"));
        add(TokenSequencePattern.compile("[{ner:ORGANIZATION}]* [{lemma:/[Mm]unicipal/}] [{ner:ORGANIZATION}]*"));
        add(TokenSequencePattern.compile("[{ner:ORGANIZATION}]* [{lemma:/[Aa]gency/}] [{ner:ORGANIZATION}]*"));
    }};

    public static Map<String, String> normalizePronouns = new HashMap<String, String>(){{
        put("yourself","you");
        put("yours","you");
        put("their","they");
        put("mine","i");
        put("ours","us");
        put("we","us");
    }};

    public static Map<String, AMR> forceDictionary = new HashMap<String, AMR>(){{
        AMR drugTrafficking = new AMR();
        AMR.Node traffic = drugTrafficking.addNode("t", "traffic-00");
        AMR.Node drug = drugTrafficking.addNode("d", "drug");
        drugTrafficking.addArc(traffic, drug, "ARG1");
        put("drug trafficking", drugTrafficking);
        put("drug trafficing", drugTrafficking);

        AMR governmentOrganization = new AMR();
        AMR.Node govOrg = governmentOrganization.addNode("g", "government-organization");
        AMR.Node govern = governmentOrganization.addNode("g2", "govern-01");
        governmentOrganization.addArc(govOrg, govern, "ARG0-of");
        put("government", governmentOrganization);

        AMR antiTerror = new AMR();
        AMR.Node oppose = antiTerror.addNode("o", "oppose-01");
        AMR.Node terror = antiTerror.addNode("t", "terror");
        antiTerror.addArc(oppose, terror, "ARG1");
        put("anti-terror", antiTerror);
    }};

    public static String trimSuffix(String token) {
        for (String suffix : trimmedSuffixes) {
            if (token.endsWith(suffix)) {
                return token.substring(0,token.length()-suffix.length());
            }
        }
        return token;
    }

    public static Set<String> getCommonAMRisms(AMR amr, AMR.Node node) {
        Set<String> commons;
        if (commonAMRisms.containsKey(node.title.toLowerCase())) commons = commonAMRisms.get(node.title.toLowerCase());
        else {
            if (node == amr.nullNode) return new HashSet<String>();

            String lowercaseTitle = node.title.toLowerCase();

            Set<String> commonAMRisms = new HashSet<String>(Arrays.asList(new String[]{lowercaseTitle}));

            if (lowercaseTitle.split("-").length > 0) {
                String[] parts = lowercaseTitle.split("-");
                commonAMRisms.add(parts[0]);
                if (parts.length > 1) {
                    try {
                        int ignored = Integer.parseInt(parts[1]);
                    } catch (Exception e) {
                        // If it's not an int, then add this
                        commonAMRisms.add(parts[1]);
                    }
                }
            }
            if (!lowercaseTitle.contains("-") && !amr.getParentArc(node).title.equals("mod") && !lowercaseTitle.equals("you")) {
                commonAMRisms.addAll(pronouns);
            }

            commons = commonAMRisms;
        }

        // Trim the suffixes from anything we're presenting as a common amr-ism

        Set<String> ret = new HashSet<String>();
        for (String s : commons) {
            ret.add(s);
            if (!s.equals(trimSuffix(s)))
                ret.add(trimSuffix(s));
        }
        return ret;
    }
}
