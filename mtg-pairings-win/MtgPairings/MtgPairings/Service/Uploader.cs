﻿using MtgPairings.Domain;
using RestSharp;
using System.Collections.Generic;
using System.Linq;

namespace MtgPairings.Service
{
    public class Uploader
    {
        private string url;
        private string apiKey;

        public Uploader(string url, string apiKey)
        {
            this.url = url;
            this.apiKey = apiKey;
        }

        private IRestResponse<T> Execute<T>(RestRequest request) where T : new()
        {
            var client = new RestClient(url);
            request.AddParameter("key", apiKey, ParameterType.QueryString);
            var response = client.Execute<T>(request);
            if (response.ErrorException != null)
            {
                throw response.ErrorException;
            }
            return response;
        }

        private IRestResponse Execute(RestRequest request)
        {
            var client = new RestClient(url);
            request.AddParameter("key", apiKey, ParameterType.QueryString);
            var response = client.Execute(request);
            if (response.ErrorException != null)
            {
                throw response.ErrorException;
            }
            return response;
        }

        private RestRequest createRequest(string url, Method method, object body) {
            var request = new RestRequest(method);
            request.Resource = url;
            request.RequestFormat = DataFormat.Json;
            request.AddBody(body);
            return request;
        }

        public void UploadTournament(Tournament t)
        {
            var request = createRequest("api/tournament/", Method.POST,
                new
                {
                    name = t.Name,
                    day = t.Date.ToString("yyyy-MM-dd", null),
                    rounds = t.RoundCount,
                    sanctionid = t.SanctionNumber,
                    tracking = true
                });
            Execute(request);
        }

        public void UploadTeams(string sanctionid, IEnumerable<Team> teams)
        {
            var request = createRequest("api/tournament/{sanctionid}/teams", Method.PUT,
                new { 
                    teams = teams.Select(t => new {
                        id = t.Id,
                        name = t.Name,
                        players = t.Players.Select(p => new {
                            dci = p.DciNumber,
                            name = p.Name
                        })
                    })
                });
            request.AddParameter("sanctionid", sanctionid, ParameterType.UrlSegment);
            Execute(request);
        }

        public void UploadPairings(string sanctionid, int round, IEnumerable<Pairing> pairings)
        {
            var request = createRequest("api/tournament/{sanctionid}/round-{round}/pairings", Method.PUT,
                new {
                    pairings = pairings.Select(p => new {
                        team1 = p.Team1.Name,
                        team2 = p.Team2.Select(t => t.Name).ValueOrElse(null),
                        table_number = p.Table
                    })
                });
            request.AddParameter("sanctionid", sanctionid, ParameterType.UrlSegment);
            request.AddParameter("round", round, ParameterType.UrlSegment);
            Execute(request);
        }

        public void UploadResults(string sanctionid, int round, IEnumerable<Pairing> pairings)
        {
            var request = createRequest("api/tournament/{sanctionid}/round-{round}/results", Method.PUT,
                new {
                    results = pairings.Where(p => p.Result.HasValue).Select(p => new {
                        team1 = p.Team1.Name,
                        team2 = p.Team2.Select(t => t.Name).ValueOrElse(null),
                        table_number = p.Table,
                        team1_wins = p.Result.Value.Team1Wins,
                        team2_wins = p.Result.Value.Team2Wins,
                        draws = p.Result.Value.Draws
                    })
                });
            request.AddParameter("sanctionid", sanctionid, ParameterType.UrlSegment);
            request.AddParameter("round", round, ParameterType.UrlSegment);
            Execute(request);
        }

        public void UploadSeatings(string sanctionid, IEnumerable<Seating> seatings)
        {
            var request = createRequest("api/tournament/{sanctionid}/seatings", Method.PUT,
                new {
                    seatings = seatings.Select(s => new {
                        name = s.Team.Name,
                        table_number = s.Table
                    })
                });
            request.AddParameter("sanctionid", sanctionid, ParameterType.UrlSegment);
            Execute(request);
        }
    }
}
