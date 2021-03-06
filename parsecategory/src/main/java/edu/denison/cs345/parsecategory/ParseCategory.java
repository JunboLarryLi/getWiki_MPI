/*
 Description:
 In this file, we mainly calculated the percentage of party-line for each year.

 In stage 1:
 mapper <year.rollNum, PartyVote>   --->>    Reducer <year.rollNum, if_partyline>

 In stage 2:
 mapper <year, if_partyline>    --->>  Reducer <year, percentage>

 We can see two stages' outputs from:
 http://w01:50070/explorer.html#/user/li_j8/PartyLine

 Run:
 hadoop jar target/partyline-1.0-SNAPSHOT.jar /user/valdiv_n1/partyLine/* ./PartyLine/stage1_output ./PartyLine/stage2_output
 hadoop jar target/cleandata-1.0-SNAPSHOT.jar /user/valdiv_n1/TESTWIKI/INPUT/Wiki/2015-01/ /user/valdiv_n1/TESTWIKI/OUTPUT
 hadoop jar target/wikileatics-1.0-SNAPSHOT.jar /user/valdiv_n1/TESTWIKI/INPUT/ /user/valdiv_n1/TESTWIKI/OUTPUT
 hadoop jar target/parsecategory-1.0-SNAPSHOT.jar ./parse_test_in1 ./parse_test_out3
 */

package edu.denison.cs345.parsecategory;

import java.io.IOException;
import java.util.*;
import java.net.*;
import java.io.*;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.conf.*;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

public class ParseCategory {
    public static String uncap(String string)
    {
      int str_len = string.length();
      String uncaped_str = "";
      for (int i = 0; i<str_len; i++)
      {
        if (Character.isUpperCase(string.charAt(i)))
          uncaped_str = uncaped_str + Character.toLowerCase(string.charAt(i));
        else
          uncaped_str = uncaped_str + string.charAt(i);
      }
      return uncaped_str;
    }
    // **note: Files' content are white character separated. use \w in the regEx**
    public static class Map_1 extends Mapper<LongWritable, Text, Text, Text> {
        //private final static FloatWritable one = new IntWritable(1);
        private Text KEY = new Text();
        private Text VALUE = new Text();
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException
        {
            String line;
            line = value.toString();           //Convert a line to string
            //====================== Split information in a line by whitespace characters into a list ============================
            String dateHR, views, raw_xml, bytes, Line[];

            Line = line.trim().split("\\t");  //splits the line by spaces and solve issues if there are many spaces
            if (Line.length == 4)
            {
              dateHR = Line[0];
              raw_xml = Line[1]; // This is the part that we need to parse and extrac the categoties
              views = Line[2];
              bytes = Line[3];
              // More work will be done iff the language encoding is  en, En, En.d, and en.n
              /*
               * action = query
               * format = json          --> Output Format
               * prop = categories      --> desire proterty
               * title = name           --> from the line in the input file
               * clshow = !hidden       --> public categories
               * cllimit = 5            --> only the first 5 categories
               */
              //in a line of the input file we extract the name:
              //CONSTANT String the indicates the entry of the API
              String cat, _cat, page_id, title_idx, entry, _value, urlCmd, notHidden;
              cat = "<categories>";
              _cat = "</categories>";
              page_id = "<page _idx=\"";
              title_idx = "title=\"Category:";
              notHidden = "&clshow=!hidden&cllimit=5";
            /*
            Sameple raw_xml:

            <api>
              <continue clcontinue="1092923|Cloud_computing_providers" continue="||"/>
                <query>
                  <pages>
                    <page _idx="1092923" pageid="1092923" ns="0" title="Google">
                      <categories>
                        <cl ns="14" title="Category:1998 establishments in California"/>
                        <cl ns="14" title="Category:Alphabet Inc."/>
                        <cl ns="14" title="Category:American websites"/>
                        <cl ns="14" title="Category:Artificial intelligence"/>
                        <cl ns="14" title="Category:Brands that became generic"/>
                      </categories>
                    </page>
                  </pages>
                </query>
            </api>
            */
             int s_indx, e_indx;
             s_indx = raw_xml.indexOf(page_id); // get the starting index of "<page _idx=\""

             if (s_indx != -1) // check if the XML contains page _idx such tab.
             {
               s_indx += page_id.length(); // get the index of the actually value of page_idx
               e_indx = raw_xml.indexOf("\"", s_indx); // get the ending index of the actually value of page_idx
               String idx = raw_xml.substring(s_indx, e_indx); // get the actually string of the page index_value. In above example, get 1092923
               String negtive_one = "-1";
               if (!idx.equals(negtive_one))   // check if page _idx != -1 --> if it is a valid page
               {
                 //========== index of the start and end of the Category List ==========
                 int s_cat, e_cat;
                 s_cat = raw_xml.indexOf(cat);
                 if (s_cat != -1)     // checks if the XML contains categories such
                 {
                   s_cat += cat.length();
                   e_cat = raw_xml.indexOf(_cat);
                   if (e_cat < raw_xml.length())
                   {
                     String categories = raw_xml.substring(s_cat, e_cat);
                     //====================== Split information in a line by whitespace characters into a list ============================
                     String categoryList = "";
                     int s_title = 1;
                     int e_title = 1;
                     int flag = 0;
                     while(flag != -1) // Iterates through the categories
                     {
                       flag = categories.indexOf(title_idx, e_title); // get the starting index of "title=\"Category:"
                       if (flag != -1)
                       {
                         s_title = flag + title_idx.length(); // get the starting index of the actual category
                         e_title = categories.indexOf("\"", s_title); // get the ending index of the actual category
                         String category = categories.substring(s_title, e_title); // take the substring based on the given above indices
                         String uncaped_category = uncap(category);
                         categoryList += (uncaped_category + "\t");
                       }
                     }

                     _value = categoryList + views + "\t" + bytes;
                     //  set KET and VALUE
                     KEY.set(dateHR);
                     VALUE.set(_value);
                     //COMMIT KET and VALUE
                     context.write(KEY, VALUE);
                   }
                 }
               }
             }
          }
            }

    }

    public static class Reduce_1 extends Reducer<Text, Text, Text, Text> {
        private Text VALUE = new Text();
        //private final static IntWritable one = new IntWritable(1);
        //private final static IntWritable zero = new IntWritable(0);
        public void reduce(Text key, Iterable<Text> values, Context context)
        throws IOException, InterruptedException {

            for (Text val : values)
            {
                context.write(key, val);
            }
        }
    }

    public static void main(String[] args) throws Exception {
    Configuration conf = new Configuration();
    Job job = new Job(conf, "parsecategory");
    job.setJarByClass(ParseCategory.class);
    job.setOutputKeyClass(Text.class);
    job.setOutputValueClass(Text.class);

    // job.setNumReduceTasks(4);
    job.setMapperClass(Map_1.class);
    job.setReducerClass(Reduce_1.class);
    job.setInputFormatClass(TextInputFormat.class);
    job.setOutputFormatClass(TextOutputFormat.class);

    FileInputFormat.addInputPath(job, new Path(args[0]));
    FileOutputFormat.setOutputPath(job, new Path(args[1]));

    job.waitForCompletion(true); // Can't start until the first stage is finished

  }
}
