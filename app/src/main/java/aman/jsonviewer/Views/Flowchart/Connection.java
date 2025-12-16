package aman.jsonviewer;

/**
 * Represents a connection (edge) between two nodes in the flowchart.
 */
public class Connection {
    public CardNode from;
    public CardNode to;

    public Connection(CardNode from, CardNode to) {
        this.from = from;
        this.to = to;
    }
}