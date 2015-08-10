(ns manager-web.auth
  (:require [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])))

(def users
  {"oliver" {:username "oliver"
             :password (creds/hash-bcrypt "oliver")
             :roles #{::admin}}})
