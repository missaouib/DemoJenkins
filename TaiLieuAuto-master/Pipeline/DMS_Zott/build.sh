#!/bin/bash
cmd=$(basename $0)

sub_help(){
    echo "Usage: $cmd <subcommand> [options]"
    echo "Subcommands:"
    echo "    help              Show help"
    echo "    Check        Check change source code"
    echo ""
}
sub_Check(){
    if [[ $(git diff HEAD~ --name-only | grep WarFile) = *WarFile* ]]; then
        echo "change file war or yml"
    fi
}
subcommand=$1

case $subcommand in
    "" | "-h" | "--help")
        sub_help
        ;;
    *)
        shift
        sub_${subcommand} $@
        if [ $? = 127 ]; then
            echo "Error: '$subcommand' is not a known subcommand." >&2
            echo "Run '$cmd --help' for a list of known subcommands." >&2
            exit 1
        fi
        ;;
esac
