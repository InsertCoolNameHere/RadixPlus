#!/usr/bin/env bash
################################################################################
# galileo-cluster - manage a Galileo cluster
#
# This is a basic convenience script that will SSH to the machines in the
# cluster and execute galileo-node on them with any relevant command line
# options.
################################################################################

colorize=""
node_opts=""
ssh_opts=""
if [[ -n "${GALILEO_SSH_OPTS}" ]]; then
    ssh_opts="${GALILEO_SSH_OPTS}"
fi

print_usage() {
cat <<- EOM
Usage: $(basename ${0}) [-cf] [command]

Commands:
    start (default) - starts the cluster
    stop - clean cluster shutdown
    restart - performs the 'stop' and then 'start' commands
    status - print the current status of the cluster

Options:
    -c             colorize output
    -f             forceful shutdown (SIGKILL)
    -u username    Use a specific username for SSH

The GALILEO_SSH_OPTS environment variable adds options to the ssh command line
for non-standard ssh configurations.
EOM
}

while getopts "cfu:" flag; do
    case ${flag} in
        c) colorize="-c" node_opts="${node_opts} -c" ;;
        f) node_opts="${node_opts} -f" ;;
        u) ssh_opts="${ssh_opts} -l${OPTARG}" ;;
        ?) print_usage; exit 1;
    esac
done

shift $(($OPTIND - 1))

source "$(cd "$(dirname "$0")" && pwd)/galileo-environment"

groups=$(ls "${GALILEO_CONF}"/network/*.group 2> /dev/null)
if [[ $? -ne 0 ]]; then
    echo "Error: group configuration files not found!"
    echo "Please ensure \${GALILEO_CONF}/network is populated with group files."
    exit 1
fi

for group in ${groups}; do
    # Remove comments, whitespace, empty lines, and everything after the colon
    # character (used to specify communication ports)
    sed 's/[\t ]//g; s/^#.*//g; /^$/d; s/\(.*\):.*/\1/g' "${group}" | \
        "${GALILEO_HOME}"/bin/dssh -pq ${colorize} -o "${ssh_opts}" \
        '. ~/.bash_profile > /dev/null 2>&1;. ~/.profile > /dev/null 2>&1; rm -r ${GALILEO_ROOT} ' &
done
wait
