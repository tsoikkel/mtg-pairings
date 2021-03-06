(ns mtg-pairings-server.tournament-api
  (:require [mtg-pairings-server.util :refer [response]]
            [mtg-pairings-server.tournaments :refer :all]
            [mtg-pairings-server.schema :refer :all]
            clj-time.coerce
            [compojure.api.sweet :refer :all]
            [schema.core :as s]))

(defmacro validate-request [sanction-id apikey & body]
  `(let [user# (user-for-apikey ~apikey)
         owner# (owner-of-tournament ~sanction-id)]
     (cond
       (nil? owner#) {:status 404
                      :body "Virheellinen sanktiointinumero"}
       (nil? user#) {:status 400
                     :body "Virheellinen API key"}
       (not= owner# user#) {:status 403
                            :body "Eri käyttäjän tallentama turnaus"}
       :else (do ~@body))))

(defroutes tournament-routes
  (POST "/" request
    :return {:id s/Int}
    :summary "Lisää turnaus"
    :query-params [key :- String]
    :body [tournament InputTournament {:description "Uusi turnaus"}]
    (if-let [user (user-for-apikey key)]
      (let [tournament (-> tournament
                           (update-in [:day] clj-time.coerce/to-local-date)
                           (assoc :owner user))]
        (response (select-keys (add-tournament tournament) [:id])))
      {:status 400
       :body "Virheellinen API key"}))
  (PUT "/:sanctionid" []
    :path-params [sanctionid :- s/Str]
    :query-params [key :- s/Str]
    :body [tournament {:name s/Str}]
    (save-tournament sanctionid tournament)
    {:status 204})
  (GET "/" []
    :return [Tournament]
    :summary "Hae kaikki turnaukset"
    (response (tournaments)))
  (GET "/:id" []
    :path-params [id :- s/Int]
    :return Tournament
    :summary "Hae turnaus ID:n perusteella"
    (response (tournament id)))
  (GET "/:id/round-:round/pairings" []
    :path-params [id :- s/Int
                  round :- s/Int]
    :return [Pairing]
    :summary "Hae yhden kierroksen pairingit"
    (response (get-round id round)))
  (GET "/:id/round-:round/results" []
    :path-params [id :- s/Int
                  round :- s/Int]
    :return [Pairing]
    :summary "Hae yhden kierroksen tulokset"
    (response (get-round id round)))
  (GET "/:id/round-:round/standings" []
    :path-params [id :- s/Int
                  round :- s/Int]
    :return [Standing]
    :summary "Hae kierroksen jälkeiset standingsit"
    (response (standings-for-api id round false)))
  (GET "/:id/seatings" []
    :path-params [id :- s/Int]
    :summary "Hae seatingit"
    (response (seatings id)))
  (GET "/:id/pods-:number" []
    :path-params [id :- s/Int
                  number :- s/Int]
    :summary "Hae podit"
    (response (pods id number)))
  (GET "/:id/coverage" []
    :path-params [id :- s/Int]
    :query-params [key :- s/Str]
    :summary "Hae coveragen käyttöön uusimmat standingit, pairingit ja pelaajien historia"
    (let [sanction-id (id->sanctionid id)]
      (validate-request sanction-id key
        (response (coverage id)))))
  (PUT "/:sanctionid/round-:round/pairings" []
    :path-params [sanctionid :- s/Str
                  round :- s/Int]
    :body [pairings InputPairings {:description "pairings"}]
    :summary "Lisää kierroksen pairingit"
    :query-params [key :- String]
    (validate-request sanctionid key
      (add-pairings sanctionid round (:pairings pairings))
      {:status 204}))
  (PUT "/:sanctionid/round-:round/results" []
    :path-params [sanctionid :- s/Str
                  round :- s/Int]
    :body [results InputResults {:description "results"}]
    :summary "Lisää kierroksen tulokset"
    :query-params [key :- String]
    (validate-request sanctionid key
      (add-results sanctionid round (:results results))
      {:status 204}))
  (PUT "/:sanctionid/round-:round/results/publish" []
    :path-params [sanctionid :- s/Str
                  round :- s/Int]
    :summary "Julkaisee kierroksen tulokset"
    :query-params [key :- String]
    (validate-request sanctionid key
      (publish-results sanctionid round)
      {:status 204}))
  (PUT "/:sanctionid/seatings" []
    :path-params [sanctionid :- s/Str]
    :body [seatings InputSeatings {:description "seatings"}]
    :summary "Lisää turnauksen seatingit"
    :query-params [key :- String]
    (validate-request sanctionid key
      (add-seatings sanctionid (:seatings seatings))
      {:status 204}))
  (PUT "/:sanctionid/teams" []
    :path-params [sanctionid :- s/Str]
    :body [teams InputTeams {:description "teams"}]
    :summary "Lisää turnauksen tiimit"
    :query-params [key :- String]
    (validate-request sanctionid key
      (add-teams sanctionid (:teams teams))
      {:status 204}))
  (DELETE "/:sanctionid" []
    :path-params [sanctionid :- s/Str]
    :summary "Poistaa kaikki turnauksen tiedot"
    :query-params [key :- String]
    (validate-request sanctionid key
      (delete-tournament sanctionid)
      {:status 204}))
  (DELETE "/:sanctionid/round-:round" []
    :path-params [sanctionid :- s/Str
                  round :- s/Int]
    :query-params [key :- s/Str]
    :summary "Poista turnauksesta kierros"
    (validate-request sanctionid key
      (delete-round sanctionid round)
      {:status 204}))
  (PUT "/:sanctionid/pods" []
    :summary "Lisää draftipodin tiedot"
    :query-params [key :- String]
    :path-params [sanctionid :- s/Str]
    :body [pods [InputPodRound]]
    (validate-request sanctionid key
      (add-pods sanctionid pods)
      {:status 204})))
