import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:jeho_own_flutter/data/jeho_api_client.dart';

void main() {
  group('JEHO agency API contract', () {
    test('agency pricing uses the existing public controller route', () async {
      late Uri requestedUri;
      final client = JehoApiClient(
        baseUrl: 'https://example.test/api/v1/',
        client: MockClient((request) async {
          requestedUri = request.url;
          return http.Response(jsonEncode({'price': 0}), 200,
              headers: {'content-type': 'application/json'});
        }),
      );

      final result = await client.getAgencyPricing();

      expect(requestedUri.path, '/api/v1/agencies/pricing');
      expect(requestedUri.query, isEmpty);
      expect(result['price'], 0);
    });

    test('agency member list encodes filters and uses bearer auth', () async {
      late http.Request requested;
      final client = JehoApiClient(
        baseUrl: 'https://example.test/api/v1/',
        client: MockClient((request) async {
          requested = request;
          return http.Response(jsonEncode({'items': [{'id': 'member-1'}]}), 200,
              headers: {'content-type': 'application/json'});
        }),
      );

      final members = await client.getAgencyMembers(
        'test-access-token',
        'agency-1',
        query: 'Ali & Sara',
        page: 2,
        limit: 10,
        scope: 'active',
      );

      expect(requested.url.path, '/api/v1/agencies/agency-1/members');
      expect(requested.url.queryParameters['q'], 'Ali & Sara');
      expect(requested.url.queryParameters['page'], '2');
      expect(requested.url.queryParameters['limit'], '10');
      expect(requested.url.queryParameters['scope'], 'active');
      expect(requested.headers['authorization'], 'Bearer test-access-token');
      expect(members, hasLength(1));
      expect(members.single['id'], 'member-1');
    });

    test('payout request creation targets the server-owned payout route', () async {
      late http.Request requested;
      final client = JehoApiClient(
        baseUrl: 'https://example.test/api/v1/',
        client: MockClient((request) async {
          requested = request;
          return http.Response(jsonEncode({'id': 'payout-1', 'status': 'pending'}), 201,
              headers: {'content-type': 'application/json'});
        }),
      );

      final result = await client.createAgencyPayoutRequest(
        'test-access-token',
        'agency-1',
        {'diamonds': 1000, 'method': 'agency'},
      );

      expect(requested.method, 'POST');
      expect(requested.url.path, '/api/v1/agencies/agency-1/payout-requests');
      expect(jsonDecode(requested.body), {'diamonds': 1000, 'method': 'agency'});
      expect(result['status'], 'pending');
    });
  });
}
