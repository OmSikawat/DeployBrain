import axios from "axios";

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  auth: {
    username: import.meta.env.VITE_API_USER,
    password: import.meta.env.VITE_API_PASS,
  },
});

export const getBuilds = (page = 0, size = 20) =>
  client.get(`/api/builds?page=${page}&size=${size}`).then((r) => r.data);

export const getBuildDetail = (id) =>
  client.get(`/api/builds/${id}`).then((r) => r.data);

export const getFailureTypeStats = () =>
  client.get(`/api/stats/failure-types`).then((r) => r.data);

export const getMttrStats = () =>
  client.get(`/api/stats/mttr`).then((r) => r.data);

export const getFixRateStats = () =>
  client.get(`/api/stats/fix-rate`).then((r) => r.data);