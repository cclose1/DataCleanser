/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.cbc.datacleanser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import org.cbc.utils.system.CommandLineReader;
import org.cbc.utils.system.Logger;

/**
 *
 * @author Chris
 */
public class CleanOutboundPostCodes {
    private class LineReader {
        BufferedReader br;
        String[]       fields;
        int            count = 0;
        
        /*
         * BufferedReader readLine teminates line on \n or \n\r. For the data being read there are stand alone
         * \n characters which are not true end of lines.
         */
        private String readLine() throws IOException {
            int ch;
            StringBuilder ln = new StringBuilder();
            
            while ((ch = br.read()) != -1) {
                if (ch == '\r') break;
                if (ch != '\n') ln.append((char)ch);
            }
            if (ch == -1)
                ch = -1;
            return ch == -1 && ln.length() == 0? null : ln.toString();
        }
        public LineReader(String inFile) throws FileNotFoundException, IOException {
            br = new BufferedReader(new FileReader(inFile));
            
            if (!next()) throw new IOException("Post code file is empty");
        };
        public boolean next() throws IOException {
            String line;
            
            line = readLine();
            
            if (line == null) return false;
            
            count++;
            fields = line.split("\\t");
            
            if (fields.length != 4) 
                throw new IOException("Post code line " + count + " has " + fields.length + " but 4 are required");

            return true;
        }
        public String getField(int index) {
            return fields[index].trim();
        }
        public int getLineNo() {
            return count;
        }
        public void close() throws IOException {
            br.close();
        }
    }

    private class CleanPostCode {
        private String  postCode;
        private boolean nonGeo = false;
        private boolean shared = false;

        public void setPostCode(String code) {
            boolean skip = false;

            postCode = code.trim();
            nonGeo   = postCode.indexOf("non") >= 0;
            shared   = postCode.indexOf("shared") >= 0;

            if (!isNonGeo() && !isShared()) {
                return;
            }
            StringBuilder clean = new StringBuilder();

            for (int i = 0; i < postCode.length(); i++) {
                char ch = postCode.charAt(i);

                if (ch == '[') {
                    skip = true;
                }
                if (ch == ']') {
                    skip = false;
                }
                if ((ch >= 'A' && ch <= 'Z' || ch >= '0' && ch <= '9') && !skip) {
                    clean.append(ch);
                }
            }
            postCode = clean.toString().trim();
        }

        public CleanPostCode(String code) {
            setPostCode(code);
        }

        /**
         * @return the postCode
         */
        public String getPostCode() {
            return postCode.length() == 0? "XXINV" : postCode;
        }

        /**
         * @return the nonGeo
         */
        public boolean isNonGeo() {
            return nonGeo;
        }

        /**
         * @return the shared
         */
        public boolean isShared() {
            return shared;
        }
    }
    private LineReader setSource(String file) throws FileNotFoundException, IOException {
        return new LineReader(file);
    }
    private CleanPostCode getCleanPostCode(String code) {
        return new CleanPostCode(code);
    }
    private char setCase(char ch, boolean upper) {
        if (ch >= 'a' && ch <= 'z' &&  upper) ch += 'A' - 'a';
        if (ch >= 'A' && ch <= 'Z' && !upper) ch -= 'A' - 'a';
        
        return ch;
    }
    private String capitalise(String value) {
        boolean       upper    = true;
        char          ch;        
        StringBuilder capValue = new StringBuilder();
        
        value = value.trim();
        
        for (int i = 0; i < value.length(); i++) {
            ch = value.charAt(i);
            capValue.append(setCase(ch, upper));
            upper = ch == ' ' || ch == '-';
        }
        return capValue.toString();
    }
    /**
     * @param args the command line arguments
     */
    
    public static void main(String[] args) {        
        CommandLineReader      cmd     = new CommandLineReader();
        Logger                 log     = new Logger();
        CleanOutboundPostCodes load    = new CleanOutboundPostCodes();
        String                 version = "V1.0 Released 15-Mar-2016";
        LineReader             reader;

        cmd.addParameter("InFile");
        cmd.addParameter("OutFile");
        cmd.load("CleanPostCodes", version, args, false);
        log.setTimePrefix("HH:mm:ss.SSS");
        log.setLogException(true);
        
        try {
            BufferedWriter out   = new BufferedWriter(new FileWriter(cmd.getString("OutFile")));
            log.comment("Reading from " + cmd.getString("InFile") + " to "  + cmd.getString("OutFile"));
            reader = load.setSource(cmd.getString("InFile"));
            out.write("Area,Outbound,Town,County,Shared,NonGeo");
            out.newLine();
            
            while (reader.next()) {
                String   area   = reader.getField(0);
                String[] codes  = reader.getField(1).split(",");
                String   town   = load.capitalise(reader.getField(2));
                String   county = reader.getField(3);
                
                if (county.startsWith("(")) county = county.substring(1);
                if (county.endsWith(")"))   county = county.substring(0, county.length() - 1);
                
                for (String code : codes) {
                    CleanPostCode outcode = load.getCleanPostCode(code);

                    if (code.length() > 0) {
                        out.write(area + ',');
                        out.write(outcode.getPostCode() + ',');
                        out.write(town + ',');
                        out.write(county + ',');
                        out.write((outcode.isShared()? "Y" : "") + ',');
                        out.write(outcode.isNonGeo()? "Y" : "");
                        out.newLine();
                        
                        if (log != null) {
                            if (outcode.isNonGeo()) {
                                log.comment("At line " + reader.getLineNo()+ " code " + outcode.getPostCode() + " non-geo");
                            }
                            if (outcode.isShared()) {
                                log.comment("At line " + reader.getLineNo() + " code " + outcode.getPostCode() + " shared");
                            }
                        }
                    }
                }
            }
            out.close();
            reader.close();
        } catch (CommandLineReader.CommandLineException ex) {
            log.error("Command line error-" + ex.getMessage());
        } catch (FileNotFoundException ex) {
            log.error("File error-" + ex.getMessage());
        } catch (IOException ex) {
            log.error("File error-" + ex.getMessage());
        }
        System.out.println("Finished");
    }    
}
