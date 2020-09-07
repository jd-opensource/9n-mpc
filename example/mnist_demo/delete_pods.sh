set -x
role=$1
if [ $role -eq 1 ];then
    name_space='fl-leader'
else
    name_space='fl-follower'
fi
task=`kubectl get pods -n ${name_space} --no-headers=true | awk '/dc-|tfjob-/{print $1}'|awk -F'-' '{print $2}'|head -n1`
python ../../src/ResourceManager/delete_job.py $role $task
