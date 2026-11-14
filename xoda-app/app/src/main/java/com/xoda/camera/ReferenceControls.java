package com.xoda.camera;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Independent implementation inventory. Order/short functional labels were
 * reconstructed from the user's screenshots and 10.4.2 Korean config.
 * No SODA JSON, icons, textures, source or models are embedded.
 */
public final class ReferenceControls {
    public static final String SHAPES="얼굴 스타일", BASIC="뷰티", ADVANCED="고급 뷰티",
            NOSE3D="3D 코", RATIOS="얼굴 비율", MAKEUP="메이크업";
    public static final class Item {
        public final String category,key,title,kind;
        public final float initial;
        public final boolean approximation;
        Item(String c,String k,String t,float d,String type,boolean approx) {
            category=c;key=k;title=t;initial=d;kind=type;approximation=approx;
        }
    }
    public static final List<Item> ALL;
    static {
        List<Item> a=new ArrayList<>();
        // Original face styles: base config default 0.35 for five non-original styles.
        add(a,SHAPES,"original|원본|0","daily|데일리|.35","natural|내추럴|.35",
                "baby|베이비|.35","slim_style|슬림|.35","beauty_style|뷰티|.35");
        // 18 Korean capture beauty items, in the native configuration order.
        add(a,BASIC,"faceratio|얼굴 비율|0","bodyslim|슬리밍|0","head|머리 작게|0",
                "facesize|얼굴 크기|0","smooth|매끈하게|.10","balance|모공|0",
                "clear|고화질|0","toneup|피부톤|0","enlarge|눈 크기|.20",
                "narrow|코 좁게|.15","wrinkle|팔자주름|.35","cheekbone|광대뼈|0",
                "slim|슬림하게|0","short|얼굴 짧게|0","brow|이마|0",
                "clarity|눈 선명하게|.50","eyebag|다크서클|.50","teeth|치아미백|.30");
        // 42 native advanced capture entries. Six named 3D effects are 2D
        // screen-space estimates until a licensed dense-depth engine is available.
        add(a,ADVANCED,
                "face_lifting|3D 리프팅|0","face_forehead|3D 이마|0",
                "nose_menu|3D 코|0","face_applecheeks|3D 애플존|0",
                "face_correction|3D 입|0","mouth_plump|3D 입술|0",
                "position|눈 위치|0","eyelength|뒤트임|0","philtrum|인중|0",
                "alar|콧볼|0","mouth|입 크기|0","upperlip|윗입술|0",
                "lowerlip|아랫입술|0","lift|브이라인|0","temple|관자놀이|0",
                "jawbone|턱뼈|0","chin|턱 길이|0","vshape|뾰족한 턱|0",
                "eyesize|눈 높이|0","eyewidth|눈 너비|0","span|눈 간격|0",
                "corner|눈 각도|0","pupil|눈동자|0","inner|앞트임|0",
                "under|밑트임|0","eyetail|눈꼬리|0","plim|애교살|0",
                "nose|코 크기|0","noselength|코 길이|0","bridge|콧등|0",
                "radixnasi|콧부리|0","tip|코끝|0","lipwidth|입 너비|0",
                "mlip|M자형 입술|0","smile|입꼬리|0","thick|눈썹 두께|0",
                "eyebrowlift|눈썹 높이|0","glabella|미간|0",
                "eyebrow|눈썹 위치|0","shape|눈썹 모양|0",
                "front|눈썹 앞머리|0","behind|눈썹 꼬리|0");
        add(a,NOSE3D,"nose_nose|3D 코 크기|0","nose_bridge|3D 콧등|0",
                "nose_alar|3D 콧볼|0","nose_root|3D 코뿌리|0","nose_tip|3D 코끝|0");
        add(a,RATIOS,"mfeyebrownose|중안부 길이|0","ratioshrink|얼굴 세로 비율|0",
                "ratiolowerface|하관 비율|0","ratioheadtop|정수리 비율|0",
                "ratioforehead|이마 비율|0","mfphiltrum|인중 비율|0");
        // Proprietary SODA makeup inventory is not established by the 77 capture
        // beauty JSON entries. These six are independent XODA makeup targets.
        add(a,MAKEUP,"lipstick|립스틱|0","blusher|블러셔|0",
                "eyeshadow|아이섀도|0","eyeliner|아이라인|0",
                "eyelashes|속눈썹|0","eyebrows|눈썹|0");
        ALL=Collections.unmodifiableList(a);
    }
    private static void add(List<Item> out,String category,String... rows){
        for(String row:rows){
            String[] v=row.split("\\|");
            String key=v[0];
            boolean style=category.equals(SHAPES);
            boolean shape=category.equals(ADVANCED)||category.equals(NOSE3D)||category.equals(RATIOS);
            out.add(new Item(category,key,v[1],Float.parseFloat(v[2]),
                    style?"style":makeup(category)?"makeup":isSkin(key)?"skin":"geometry",shape));
        }
    }
    private static boolean makeup(String c){return MAKEUP.equals(c);}
    private static boolean isSkin(String key){
        switch(key){
            case "smooth":case "balance":case "clear":case "toneup":
            case "wrinkle":case "clarity":case "eyebag":case "teeth":
                return true;
            default:return false;
        }
    }
    public static List<Item> group(String category){
        List<Item> result=new ArrayList<>();
        for(Item i:ALL)if(i.category.equals(category))result.add(i);
        return result;
    }
    public static Item find(String key) {
        for(Item i:ALL)if(i.key.equals(key))return i;
        throw new IllegalArgumentException("Unknown beauty control: "+key);
    }
    public static int count(String category){return group(category).size();}
    private ReferenceControls(){}
}
