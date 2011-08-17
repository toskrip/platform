/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.data.dialect;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.SQLFragment;

import java.util.regex.Pattern;

/*
* User: adam
* Date: Aug 17, 2011
* Time: 6:52:39 AM
*/
public class RegularExpressionTest extends Assert
{
    @Test
    public void testAllDialects()
    {
        for (SqlDialect dialect : SqlDialectManager.getAllDialectsToTest())
            testParameterSubstitution(dialect);
    }

    private void testParameterSubstitution(SqlDialect dialect)
    {
        String longString = "MASSAHLVTIKRSGDDGAHFPLSLSSCLFGRSIECDIRIQLPVVSQRHCPIVVQEQEAILYNFSSTNPTQVNGVTIDEPVRLRHGDIITII" +
            "DRSFRYEDGNHEDGSKPTEFPGKSLGKEPSRRASRDSFCADPDGEGQDTKASKMTASRRSFVYAKGLSADSPASDGSKNSVSQDSSGHVEQHTGRNIVEPTSGGSLL" +
            "RSPGLQGAVTGNRSLLPTQSLSNSNEKESPFEKLYQSMKEELDVKSQKSCRKSEPQPDRAAEESRETQLLVSGRARAKSSGSTPVTAASSPKVGKIWTERWRGGMVP" +
            "VQTSTETAKMKTPVRHSQQLKDEDSRVTGRRHSVNLDEGGSAQAVHKTVTPGKLATRNQTPVEAGDVGSPADTPEHSSSPQRSIPAKVEAPSAETQNRLSLTQRLVP" +
            "GEKKTPKGSFSKPEKLATAAEQTCSGLPGLSSVDISNFGDSINKSEGMPMKRRRVSFGGHLRPELFDENLPPNTPLKRGETPTKRKSLGTHSPAVLKTIIKERPQSP" +
            "GKQESPGITPPRTNDQRRRSGRTSSGSNFLCETDIPKKAGRKSGNLPAKRASISRSQHGILQMICSKRRSGASEANLIVAKSWADVVKLGVKQTQTKVAKHVPPKQT" +
            "SKRQRRPSTPKKPTSNLHNQFTTGHANSPCTIVVGRAQIEKVSVPARPYKMLNNLMLNRKVDFSEDLSGLTEMFKTPVKEKQQQMSDTGSVLSNSANLSERQLQVTN" +
            "SGDIPEPITTEILGEKVLSSTRNAAKQQSDRYSASPTLRRRSIKHENTVQTPKNVHNITDLEKKTPVSETEPLKTASSVSKLRRSRELRHTLVETMNEKTEAVLAEN" +
            "TTARHLRGTFREQKVDQQVQDNENAPQRCKESGELSEGSEKTSARRSSARKQKPTKDLLGSQMVTQTADYAEELLSQGQGTIQNLEESMHMQNTSISEDQGITEKKV" +
            "NIIVYATKEKHSPKTPGKKAQPLEGPAGLKEHFETPNPKDKPITEDRTRVLCKSPQVTTENITTNTKPQTSTSGKKVDMKEESSALTKRIHMPGESRHNPKILKLEC" +
            "EDIKALKQSENEMLTSTVNGSKRTLGKSKKKAQPLEDLTCFQELFISPVPTNIIKKIPSKSPHTQPVRTPASTKRLSKTGLSKVDVRQEPSTLGKRTKSPGRAPGTP" +
            "APVQEENDCTAYMETPKQKLESIENLTGLRKQSRTPKDITGFQDSFQIPDHANGPLVVVKTKKMFFNSPQPESAITRKSRERQSRASISKIDVKEELLESEEHLQLG" +
            "EGVDTFQVSTNKVIRSSRKPAKRKLDSTAGMPNSKRMRCSSKDNTPCLEDLNGFQELFQMPGYANDSLTTGISTMLARSPQLGPVRTQINKKSLPKIILRKMDVTEE" +
            "ISGLWKQSLGRVHTTQEQEDNAIKAIMEIPKETLQTAADGTRLTRQPQTPKEKVQPLEDHSVFQELFQTSRYCSDPLIGNKQTRMSLRSPQPGFVRTPRTSKRLAKT" +
            "SVGNIAVREKISPVSLPQCATGEVVHIPIGPEDDTENKGVKESTPQTLDSSASRTVSKRQQGAHEERPQFSGDLFHPQELFQTPASGKDPVTVDETTKIALQSPQPG" +
            "HIINPASMKRQSNMSLRKDMREFSILEKQTQSRGRDAGTPAPMQEENGTTAIMETPKQKLDFIGNSTGHKRRPRTPKNRAQPLEDLDGFQELFQTPAGASDPVSVEE" +
            "SAKISLASSQAEPVRTPASTKRRSKTGLSKVDVRQEPSTLGKRMKSLGRAPGTPAPVQEENDSTAFMETPKQKLDFTGNSSGHKRRPQTPKIRAQPLEDLDGFQELF" +
            "QTPAGANDSVTVEESVKMSLESSQAEPVKTPASTKRLSKTGLSKVDVREDPSILEKKTKSPGTPAPVQEENDCTAFMETPKQKLDFTGNSSGHKRRPRTPKIRAQPL" +
            "EDLDGFQELFQTPAGASDSVTVEESAKMSLESSQAKPVKTPASTKRLSKTGLSKVDVREDPSTLGKKTKSPGRAPGTPAPVQEENDSTAFMETPKQKLDFAENSSGS" +
            "KRRSRTSKNRSQPLEDLDGFQELFQTPAGASNPVSVEESAKISLESSQAEPVRTRASTKRLSKTGLNKMDVREGHSPLSKSSCASQKVMQTLTLGEDHGRETKDGKV" +
            "LLAQKLEPAIYVTRGKRQQRSCKKRSQSPEDLSGVQEVFQTSGHNKDSVTVDNLAKLPSSSPPLEPTDTSVTSRRQARTGLRKVHVKNELSGGIMHPQISGEIVDLP" +
            "REPEGEGKVIKTRKQSVKRKLDTEVNVPRSKRQRITRAEKTLEDLPGFQELCQAPSLVMDSVIVEKTPKMPDKSPEPVDTTSETQARRRLRRLVVTEEPIPQRKTTR" +
            "VVRQTRNTQKEPISDNQGMEEFKESSVQKQDPSVSLTGRRNQPRTVKEKTQPLEELTSFQEETAKRISSKSPQPEEKETLAGLKRQLRIQLINDGVKEEPTAQRKQP" +
            "SRETRNTLKEPVGDSINVEEVKKSTKQKIDPVASVPVSKRPRRVPKEKAQALELAGLKGPIQTLGHTDESASDKGPTQMPCNSLQPEQVDSFQSSPRRPRTRRGKVE" +
            "ADEEPSAVRKTVSTSRQTMRSRKVPEIGNNGTQVSKASIKQTLDTVAKVTGSRRQLRTHKGWGSTLLKLLGDSKEITQISDHSEKLAHDTSILKSTQQQKPDSVKPL" +
            "RTCRRVLRASKEVPKEVLVDTRDHATLQSKSNPLLSPKRKSARDGSIVRTRALRSLAPKQEATDEKPVPEKKRAASSKRYVSPEPVKMKHLKIVSNKLESVEEQVST" +
            "VMKTEEMEAKRENPVTPDQNSRYRKKTNVKQPRPKFDASAENVGIKKNEKTMKTASQETELQNPDDGAKKSTSRGQVSGKRTCLRSRGTTEMPQPCEAEEKTSKPAA" +
            "EILIKPQEEKGVSGESDVRCLRSRKTRVALDSEPKPRVTRGTKKDAKTLKEDEDIVCTKKLRTRS";

        String longLiteralSql = "WHERE (Run IN (88)) AND (position(TrimmedPeptide IN '" + longString + "') > 0 )";
        testParameterSubstitution(dialect, new SQLFragment(longLiteralSql), longLiteralSql);
        String longIdentifierSql = "WHERE (\"" + longString + "\" IN (88)) AND (position(TrimmedPeptide IN ('FOO')) > 0 )";
        testParameterSubstitution(dialect, new SQLFragment(longIdentifierSql), longIdentifierSql);
        String longBothSql = "WHERE (\"" + longString + "\" IN (88)) AND (position(TrimmedPeptide IN ('" + longString + "')) > 0 )";
        testParameterSubstitution(dialect, new SQLFragment(longBothSql), longBothSql);

        testParameterSubstitution(dialect, new SQLFragment("? ? ?", 937, "this", 1.234), "'937' 'this' '1.234'");
        testParameterSubstitution(dialect, new SQLFragment("TEST ? TEST ? TEST ?", 937, "this", 1.234), "TEST '937' TEST 'this' TEST '1.234'");
        testParameterSubstitution(dialect, new SQLFragment("'????' ? '???''????' ? ?", 937, "this", 1.234), "'????' '937' '???''????' 'this' '1.234'");
        testParameterSubstitution(dialect, new SQLFragment("\"identifier\" ? \"iden?tif?ier\" '????' ? '???''????' \"iden?tifi?er\" ? ? ?", 937, 123, "this", "that", 1.234), "\"identifier\" '937' \"iden?tif?ier\" '????' '123' '???''????' \"iden?tifi?er\" 'this' 'that' '1.234'");

        // String literal escaping rules vary by dialect and database settings.  Make sure quoting and parsing are consistent.
        String lit1 = dialect.getStringHandler().quoteStringLiteral("th?is'th?at");
        String lit2 = dialect.getStringHandler().quoteStringLiteral("th?is\\'th?at");
        String lit3 = dialect.getStringHandler().quoteStringLiteral("th'?'is\\?\\th\\'\\'at");
        Pattern litPattern = dialect.getStringHandler().getStringLiteralPattern();
        assert litPattern.matcher(lit1).matches();
        assert litPattern.matcher(lit2).matches();
        assert litPattern.matcher(lit3).matches();
        String prefix = lit1 + " " + lit2 + " " + lit3 + " ";

        testParameterSubstitution(dialect, new SQLFragment(prefix + "? ? ?", 456, "this", 7.8748), prefix + "'456' 'this' '7.8748'");
    }

    private void testParameterSubstitution(SqlDialect dialect, SQLFragment fragment, String expected)
    {
        String sub = dialect.substituteParameters(fragment);
        assertEquals("Failed substitution", expected, sub);
        // fragment.toString() + " failed substitution (" + sub + ")");
    }
}
