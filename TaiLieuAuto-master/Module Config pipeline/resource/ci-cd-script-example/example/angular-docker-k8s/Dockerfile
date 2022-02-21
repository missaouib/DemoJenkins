FROM nginx

WORKDIR /

RUN rm -rf /usr/share/nginx/html/*
COPY ./dist/ROOT /usr/share/nginx/html

EXPOSE 9696

CMD ["/bin/sh",  "-c",  "envsubst < /usr/share/nginx/html/assets/env.template.js > /usr/share/nginx/html/assets/env.js && exec nginx -g 'daemon off;'"]