package uk.ac.ebi.pride.scripts;

/**
 * User: ntoro
 * Date: 08/05/2014
 * Time: 16:21
 */
public class InputPeptideSearch {

    private String sequence;
    private String proteinName;

    public String getSequence() {
        return sequence;
    }

    public void setSequence(String sequence) {
        this.sequence = sequence;
    }

    public String getProteinName() {
        return proteinName;
    }

    public void setProteinName(String proteinName) {
        this.proteinName = proteinName;
    }

    @Override
    public String toString() {
        return "sequence='" + sequence + '\'' +
                ", proteinName='" + proteinName + '\'';
    }
}

