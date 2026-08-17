package io.github.avery07.command;

import java.util.List;

/** Runs several commands as one undoable step (e.g. deleting or moving a multi-selection). */
public final class CompositeCommand implements Command {

    private final List<Command> commands;
    private final String label;

    public CompositeCommand(String label, List<Command> commands) {
        this.label = label;
        this.commands = List.copyOf(commands);
    }

    @Override
    public void execute() {
        for (Command c : commands) {
            c.execute();
        }
    }

    @Override
    public void undo() {
        for (int i = commands.size() - 1; i >= 0; i--) {
            commands.get(i).undo();
        }
    }

    @Override
    public String label() {
        return label;
    }
}
