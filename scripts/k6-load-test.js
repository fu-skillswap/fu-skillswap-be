import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 50 },  // Ramp up to 50 users
    { duration: '1m', target: 100 },  // Ramp up to 100 users
    { duration: '3m', target: 100 },  // Hold 100 users for 3 minutes
    { duration: '1m', target: 200 },  // Spike to 200 users
    { duration: '3m', target: 200 },  // Hold 200 users for 3 minutes
    { duration: '30s', target: 0 },   // Ramp down to 0
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'], // 95% of requests should be below 1000ms
    http_req_failed: ['rate<0.01'],    // Error rate should be less than 1%
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';

export default function () {
  // 1. Lấy danh sách Campuses (Có Cache)
  let campusRes = http.get(`${BASE_URL}/api/v1/academic/catalog/campuses`);
  check(campusRes, {
    'campuses status is 200': (r) => r.status === 200,
  });

  sleep(1);

  // 2. Lấy danh sách Mentor Catalog Recommendations
  let mentorRes = http.get(`${BASE_URL}/api/v1/mentors/discovery?page=0&size=10`);
  check(mentorRes, {
    'mentors status is 200': (r) => r.status === 200,
  });

  sleep(2);
}
