#!/bin/bash

CONTAINER_NAME="kamon-grafana-dashboard"
if [ $(docker ps -a | grep $CONTAINER_NAME | awk '{print $NF}' | wc -l) -gt 0 ];then
  docker stop $CONTAINER_NAME
	docker kill $CONTAINER_NAME
	docker rm $CONTAINER_NAME
fi

docker_home=$(pwd)/docker
grafana_home=$docker_home/grafana
grafana_web_home=$docker_home/grafana_web
statsd_home=$docker_home/statsd
graphite_home=$docker_home/graphite

sudo rm -fr $docker_home/logs/* 
sudo rm -fr $grafana_home/data/* $grafana_home/logs/*
sudo rm -fr $grafana_web_home/data/* $grafana_web_home/logs/*
sudo rm -fr $graphite_home/whisper/*

#  -v $grafana_home:/etc/grafana \
docker run -d -p 80:80 -p 8125:8125/udp -p 8126:8126 \
  -v $grafana_home/data:/var/lib/grafana \
  -v $grafana_home/logs:/var/log/grafana \
  -v $grafana_web_home/data:/opt/grafana/data \
  -v $grafana_web_home/logs:/opt/grafana/data/log \
  -v $docker_home:/etc/supervisor/conf.d/ \
  -v $docker_home/logs:/var/log/supervisor \
  -v $graphite_home/whisper:/opt/graphite/storage/whisper/ \
  --name kamon-grafana-dashboard kamon/grafana_graphite
