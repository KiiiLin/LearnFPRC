package io.github.autotaskrun;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import io.github.autotaskrun.PointsEntryLocator.Label;
import static org.junit.Assert.*;

public class QuizHintTextTest {
    private static final String STEM="根据《党委（党组）落实全面从严治党主体责任规定》，地方党委应当将党的建设与经济社会发展同谋划、同部署、同推进、同考核，加强对本地区全面从严治党各项工作的领导。领导、支持和监督         、         、         、         、党的基层组织等落实全面从严治党主体责任，形成全面从严治党整体合力。";
    private static final String BODY="《党委（党组）落实全面从严治党主体责任规定》第六条规定，地方党委应当将党的建设与经济社会发展同谋划、同部署、同推进、同考核，加强对本地区全面从严治党各项工作的领导。领导、支持和监督党的纪律检查机关、党的工作机关、党委直属事业单位、党组（党委）和下级地方党委、党的基层组织等落实全面从严治党主体责任，形成全面从严治党整体合力。";
    private Label n(String s,int x1,int y1,int x2,int y2){return new Label(s,x1,y1,x2,y2,true);}
    private List<Label> background(){return Arrays.asList(
            n("多选题",141,398,321,473),n("1 /5",990,362,1113,509),n(STEM,108,593,1113,1304),
            n("A.",138,1424,213,1499),n("党的纪律检查机关",231,1421,1083,1502),
            n("B.",138,1625,213,1700),n("党的工作机关",231,1622,1083,1703),
            n("C.",138,1826,213,1901),n("党委直属事业单位",231,1823,1083,1904),
            n("D.",138,2030,213,2105),n("党组（党委）和下级地方党委",231,2027,1083,2108),
            n("查看提示",942,2240,1113,2297));}
    private QuizDocument document(){QuizDocument d=new QuizDocument();d.add(1220,2712,background());d.atBottom();assertTrue(d.complete());return d;}
    private QuizDocument small(String...values){
        QuizDocument d=new QuizDocument();List<Label> labels=new ArrayList<>();int y=500;
        for(int i=0;i<values.length;i++){labels.add(n((char)('A'+i)+". "+values[i],100,y,1100,y+70));y+=200;}
        labels.add(n("查看提示",942,2240,1113,2297));d.add(1220,2712,labels);d.atBottom();assertTrue(d.complete());return d;
    }
    @Test public void actualReportResolvesAllFourWithoutPixelsOrOcr(){
        List<Label> after=new ArrayList<>(background());after.add(n("提示",558,929,663,1001));after.add(n(BODY,57,1124,1164,1703));
        QuizHintText.Result result=QuizHintText.resolve(QuizHintText.stem(2712,background()),document(),true,2712,1001,background(),after);
        assertEquals(Arrays.asList("ABCD"),result.answers);assertTrue(result.method.startsWith("无障碍弹窗正文"));
    }
    @Test public void actualOptionNodesRespectLeftToRightDespiteBaselineDifferences(){
        QuizDocument d=document();assertEquals("党的纪律检查机关",d.options.get("A"));assertEquals("党的工作机关",d.options.get("B"));
        assertEquals("党委直属事业单位",d.options.get("C"));assertEquals("党组党委和下级地方党委",d.options.get("D"));
    }
    @Test public void ignoresBackgroundOptionsAndStemUnderModal(){
        assertEquals("",QuizHintText.body(2712,1001,background(),background(),true));
    }
    @Test public void readsOnlyNewPopupParagraph(){
        List<Label> after=new ArrayList<>(background());after.add(n(BODY,57,1124,1164,1703));
        assertEquals(BODY.trim(),QuizHintText.body(2712,1001,background(),after,true).trim());
    }
    @Test public void parentAndChildParagraphNodesNotDuplicated(){
        List<Label> after=Arrays.asList(n(BODY,57,1124,1164,1703),n("党的工作机关",231,1250,600,1310));
        assertEquals(BODY.trim(),QuizHintText.body(2712,1001,new ArrayList<>(),after,true).trim());
    }
    @Test public void unchangedTextAtNewPopupPositionCanBeUsed(){
        String s="今天学习正确的知识";
        assertEquals(s,QuizHintText.body(2712,1001,Arrays.asList(n(s,100,600,1100,700)),Arrays.asList(n(s,100,1100,1100,1200)),true).trim());
    }
    @Test public void fallbackToFullTextOcrWithoutRedMask(){
        List<Label> after=Arrays.asList(new Label(BODY,57,1124,1164,1703,false));
        QuizHintText.Result result=QuizHintText.resolve(QuizHintText.template(STEM),document(),true,2712,1001,background(),after);
        assertEquals(Arrays.asList("ABCD"),result.answers);assertTrue(result.method.startsWith("弹窗全文 OCR"));
    }
    @Test public void matchingOptionsElsewhereIsNotEnough(){
        assertTrue(QuizHintText.solve(QuizHintText.template(STEM),"资料中提到党的纪律检查机关和党的工作机关",document(),true).isEmpty());
    }
    @Test public void blankCountMustMatchOptionCount(){
        String stem=QuizHintText.template("领导支持和监督____、____、党的基层组织落实责任");
        assertTrue(QuizHintText.solve(stem,"领导支持和监督党的纪律检查机关党的工作机关党委直属事业单位党的基层组织落实责任",document(),true).isEmpty());
    }
    @Test public void singleChoiceUsesContextNotEveryMentionedDistractor(){
        String stem=QuizHintText.template("代表性的歌曲是____这首歌广为流传");
        assertEquals(Arrays.asList("B"),QuizHintText.solve(stem,"另外也提到错误歌曲。代表性的歌曲是南泥湾这首歌广为流传",small("错误歌曲","南泥湾"),false));
    }
    @Test public void uniqueFillAnswersPreserveGapOrder(){
        String stem=QuizHintText.template("第一项制度是____实行人民自治第二项内容是____共同发展进步");
        assertEquals(Arrays.asList("民族区域自治","团结合作"),QuizHintText.solve(stem,"第一项制度是民族区域自治实行人民自治第二项内容是团结合作共同发展进步",null,false));
    }
    @Test public void liveInputFillAllowsOverlappingInlineBounds(){
        String prefix="“政策和策略是党的",suffix="”是毛泽东在解放战争时期提出的一个重要论断，是他领导中国共产党对党的政策和策略问题进行深入探索所取得的标志性成果之一。";
        List<Label> page=Arrays.asList(n("填空题",141,398,321,473),n(prefix,108,614,546,683),n(suffix,108,614,1113,962),n("出题：“学习强国”学习平台",108,1046,690,1139));
        assertEquals(Arrays.asList("生命"),QuizHintText.solve(QuizHintText.stem(2712,page),prefix+"生命"+suffix,null,false));
    }
    @Test public void liveTerminalClickFillExcludesTilesFromStem(){
        String prefix="企业事业单位应当履行绿色低碳发展义务，并对所造成的损害依法";
        List<Label> page=Arrays.asList(n("点选填空题",141,398,438,473),n(prefix,108,605,1113,1028),
                n("。",549,1055,603,1127),n("责任",108,1223,297,1373),n("承担",321,1223,510,1373),n("出题：学习强国",108,1481,690,1574));
        assertEquals(Arrays.asList("承担责任"),QuizHintText.solve(QuizHintText.stem(2712,page),prefix+"承担责任。",null,false));
    }
    @Test public void liveShortSuffixInputFill(){
        assertEquals(Arrays.asList("科学文化"),QuizHintText.solve(QuizHintText.template("提高全国人民的____水平。"),"提高全国人民的科学文化水平。",null,false));
    }
    @Test public void liveClickFillUsesTwoTextNodesAroundInvisibleBlank(){
        String prefix="2002年4月，北大红楼成为北京新文化运动纪念馆，并正式对外开放。这是全国唯一一家全面展示";
        List<Label> page=Arrays.asList(n("点选填空题",141,398,438,473),n(prefix,108,605,1113,860),
                n("历史的综合性博物馆。",219,902,732,971),n("来源：参见《北京红色地标》",108,1040,1113,1220),
                n("新文化运动",108,1295,450,1445),n("五四",474,1295,663,1445),n("查看提示",942,1736,1113,1796));
        String stem=QuizHintText.stem(2712,page);
        assertTrue(stem.contains("展示§历史"));
        assertEquals(Arrays.asList("五四新文化运动"),QuizHintText.solve(stem,prefix+"五四新文化运动历史的综合性博物馆。",null,false));
    }
    @Test public void terminalChoiceBlankUsesLocalContext(){
        assertEquals(Arrays.asList("A"),QuizHintText.solve(QuizHintText.template("消防车执行任务，社会车辆及行人应当____。"),
                "其他车辆、船舶以及行人应当让行，不得穿插超越。",small("让行","穿插其中","超越消防车"),false));
    }
    @Test public void multipleShortAnchorsAreResolvedTogether(){
        assertEquals(Arrays.asList("ABCD"),QuizHintText.solve(QuizHintText.template("为了规范____，促进____，保障____，维护____，保护公民、法人和其他组织的合法权益，制定本法。"),
                "第一条规定，为了规范密码应用和管理，促进密码事业发展，保障网络与信息安全，维护国家安全和社会公共利益，保护公民、法人和其他组织的合法权益，制定本法。",
                small("密码应用和管理","密码事业发展","网络与信息安全","国家安全和社会公共利益"),true));
    }
    @Test public void conflictingTerminalContextsAreRejected(){
        assertTrue(QuizHintText.solve(QuizHintText.template("社会车辆及行人应当____"),"车辆及行人应当让行。车辆及行人应当停车。",small("让行","停车"),false).isEmpty());
    }
    @Test public void repeatedConflictingContextIsRejected(){
        String stem=QuizHintText.template("代表性的歌曲是____这首歌广为流传");
        assertTrue(QuizHintText.solve(stem,"代表性的歌曲是甲这首歌广为流传。代表性的歌曲是乙这首歌广为流传",null,false).isEmpty());
    }
    @Test public void missingAnchorIsRejected(){
        assertTrue(QuizHintText.solve(QuizHintText.template("____广为流传"),"南泥湾广为流传",null,false).isEmpty());
    }
    @Test public void unrecognizedWordsInsideAnswerAreRejected(){
        String stem=QuizHintText.template("本题所选答案____这里是后文内容");
        assertTrue(QuizHintText.solve(stem,"本题所选答案不是错误歌曲这里是后文内容",small("错误歌曲","南泥湾"),false).isEmpty());
    }
    @Test public void explicitAnswerLettersWorkWithoutBlank(){
        assertEquals(Arrays.asList("AC"),QuizHintText.solve("下列哪些正确","正确答案：A、C。",small("一","二","三"),true));
    }
    @Test public void unavailableExplicitLetterIsRejected(){
        assertTrue(QuizHintText.solve("下列哪些正确","正确答案：A、D。",small("一","二","三"),true).isEmpty());
    }
    @Test public void adjacentUnlabelledFillGapsAreNotGuessed(){
        assertTrue(QuizHintText.solve(QuizHintText.template("这是前面的文字____、____这是后面的文字"),"这是前面的文字甲乙这是后面的文字",null,false).isEmpty());
    }
    @Test public void nonbreakingSpacesAndUnderscoresAndParentheses(){
        assertEquals("之前文字§之后文字§结尾文字§末尾文字",QuizHintText.template("之前文字\u00a0\u00a0之后文字____结尾文字（ ）末尾文字"));
    }
    @Test public void ambiguousOptionSegmentationIsRejected(){
        String stem=QuizHintText.template("这是前面的文字____、____这是后面的文字");
        assertTrue(QuizHintText.solve(stem,"这是前面的文字甲乙丙这是后面的文字",small("甲","乙丙","甲乙","丙"),true).isEmpty());
    }
    @Test public void fillPreservesDecimalPoint(){
        assertEquals(Arrays.asList("1.5"),QuizHintText.solve(QuizHintText.template("此次测定结果____这里是后文内容"),"此次测定结果1.5这里是后文内容",null,false));
    }
    @Test public void fillPreservesLatinCapitalization(){
        assertEquals(Arrays.asList("DNA"),QuizHintText.solve(QuizHintText.template("这项物质名称____这里是后文内容"),"这项物质名称DNA这里是后文内容",null,false));
    }
    @Test public void inputPreparationDoesNotStripDecimalPointOrCase(){
        assertEquals("1.5",QuizHintText.inputValue("1.5"));assertEquals("DNA",QuizHintText.inputValue("DNA"));
    }
    @Test public void inputPreparationKeepsExistingQuotedChineseAnswerBehavior(){
        assertEquals("南泥湾",QuizHintText.inputValue("《南泥湾》"));assertEquals("民族区域自治",QuizHintText.inputValue("民族区域自治"));
    }
}
