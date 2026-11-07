/**
 * Wire types of the experiment-runner (spec section 10.2) and of the API services (section 6).
 * Field names mirror the Java DTOs one to one, so no mapping layer is needed.
 */

export type RunStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'REJECTED';

export interface ExperimentRequest {
  scenario: string;
  datasetSize?: number;
  clients?: number;
  durationSec?: number;
  repetitions?: number;
  warmupSec?: number;
  targets: string[];
}

/** One point of the per-second series recorded for resilience runs. */
export interface TimelinePoint {
  secondOffset: number;
  rps: number;
  serverErrors: number;
  p95Ms: number;
}

export interface SampleMetrics {
  repetition: number;
  rps: number;
  latencyMean: number;
  latencyP50: number;
  latencyP95: number;
  latencyP99: number;
  errorRate: number;
  cpuAvg: number | null;
  memAvg: number | null;
  httpRequests: number;
  iterations: number;
  droppedIterations: number;
  k6ExitCode: number;
  startedAt: string;
  finishedAt: string;
  timeline: TimelinePoint[] | null;
}

export interface AggregateStats {
  mean: number;
  stdDev: number;
}

/** Planner output captured for one S4 benchmark query (Q1..Q5). */
export interface ExplainSnapshot {
  query: string;
  /** INDEX, COLLECTION_SCAN, SEQUENTIAL_SCAN or UNKNOWN. */
  scanType: string;
  usedIndex: boolean;
  stages: string[];
  plan: string | null;
  error: string | null;
}

export interface TargetRunResult {
  target: string;
  resolvedTarget: string;
  baseUrl: string;
  container: string;
  variant: string | null;
  writeConcern: string | null;
  script: string;
  /** Runtime prerequisites applied through the admin API before the load started. */
  setup: string[];
  /** S4 only: where each benchmark query ran on an index or on a full scan. */
  explainPlans: ExplainSnapshot[] | null;
  samples: SampleMetrics[];
  aggregated: Record<string, AggregateStats>;
  error: string | null;
}

export interface RunProgress {
  completedSteps: number;
  totalSteps: number;
  currentStep: string;
}

export interface ExperimentRun {
  id: string;
  status: RunStatus;
  request: ExperimentRequest;
  scenarioTitle: string;
  script: string;
  startedAt: string;
  finishedAt: string | null;
  progress: RunProgress;
  /** Null in the list endpoint, which strips the per-sample detail. */
  targets: TargetRunResult[] | null;
  log: string[] | null;
  error: string | null;
  resultFile: string | null;
}

/* ---------- catalog ---------- */

export interface ReviewDto {
  rating: number;
  text: string;
  createdAt: string;
}

export interface ProductDto {
  id: string;
  sku: string;
  name: string;
  category: string;
  price: number;
  stock: number;
  attributes: Record<string, unknown> | null;
  reviews?: ReviewDto[] | null;
  reviewCount: number | null;
  ratingAvg: number | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface PageProductResponse {
  content: ProductDto[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/* ---------- cart & orders ---------- */

export interface CartItemDto {
  productId: string;
  quantity: number;
}

export interface CartResponse {
  cartId: string;
  items: CartItemDto[];
}

export interface OrderItemDto {
  productId: string;
  productName: string;
  price: number;
  quantity: number;
}

export interface OrderStatusHistoryDto {
  status: string;
  timestamp: string;
}

export interface OrderResponse {
  id: string;
  customerRef: string;
  customerName?: string | null;
  customerEmail?: string | null;
  items: OrderItemDto[];
  total: number;
  status: string;
  statusHistory: OrderStatusHistoryDto[];
  createdAt: string;
}

export interface PageOrderResponse {
  content: OrderResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Shared error model of every service (spec section 6). */
export interface ErrorResponse {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
}
